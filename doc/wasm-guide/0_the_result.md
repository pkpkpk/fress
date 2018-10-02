# Wasm Guide 0: The Result
[`fress.wasm`](https://github.com/pkpkpk/fress/blob/master/src/main/cljs/fress/wasm.cljs) is designed to interop with the [`serde_fressian::wasm`](https://github.com/pkpkpk/serde-fressian/blob/master/src/wasm/mod.rs) module


#### Rust...
```rust
use serde_fressian::error::{Error as FressError};
use serde_fressian::value::{Value};
use serde_fressian::wasm::{self};

#[no_mangle]
pub extern "C" fn echo(ptr: *mut u8, len: usize) -> *mut u8
{
    // read a value from javascript
    let val: Result<Value, FressError> = wasm::from_ptr(ptr, len);

    // from_ptr borrows, Value copies. So must own and free bytes separately.
    wasm::fress_dealloc(ptr, len);

    // serializes the result, hands ownership of resulting bytes over to js
    wasm::to_js(val)
}
```
#### CLJS...

```Clojure
(require '[fress.wasm])

(def module (atom nil))

(defn init-module
  [buffer] ; load compiled wasm binary from server/disk
  (.then (fress.wasm/instantiate buffer) ; wasm/instantiate returns native promise
    (fn [Mod] (reset! module Mod))
    (fn [err] (handle-err err))))

(defn echo [any]
  (if-let [Mod @module]
    (fress.wasm/call Mod "echo" any) ;; thats it! fress.wasm/call handles pointers for you.
    (throw (js/Error "missing module"))))
```

The `fress.wasm/IFressWasmModule` protocol offers primitives for module interop but in practice all you need is `fress.wasm/call`:

  1. __js:__ When we pass `fress.wasm/call` an object, it is converted into fressian bytes and written into wasm memory
  2. __js:__ A pointer to those bytes (and their length) is passed to the exported `"echo"` function
  3. __rust:__ The `echo` fn is called with a ptr and length. It deserializes those bytes into the `serde_fressian::value::Value` enum (which can represent any fressian type)
    - notice val is of type `Result<serde_fressian::value::Value, serde_fressian::error::Error>`
  4. __rust:__ Rust `echo` takes the __Result__ of that deserialization and serializes it right back to javascript, returning a ptr.
  5. __js:__ `fress.wasm/call` receives that pointer internally, reads from it, and returns its content back to the caller

```clojure
(assert (= (echo "hello world!") [nil "hello world!"]))

(def data [{::sym 'foo/bar
            :inst (js/Date.)
            :set #{42 true nil "😎😎😎"}
            :bytes (js/Uint8Array. #js [0 1 2])}])

(assert (= (echo data) [nil data]))
```


#### 2-tuple vectors: the poor man's Result enum
Instead of exceptions, rust has the [Result&lt;T,E&gt;][Result] enum. Functions that have a expectation of sometimes failing (io, serialization...) return `Result::Ok(T)` if they succeed, and `Result::Err(E)` if they fail. In cljs we model this with a simple vector `[?err ?ok]` where err is present when the operation fails, and nil when it succeeds.

From the rust side of things, you do not need to worry about explicitly handling errors at the serialization border because Results themselves are serializable. If the result is `Ok(T)`, it will simply serialize `T`. If the result is `Err(E)`, `E` will be serialized but with an error code prefix. `fress.wasm` will pick up on this error code and deliver an appropriate `[?err ?ok]` tuple.  

From the echo example:

```rust
let val: Result<Value, FressError> = wasm::from_ptr(ptr, len);
//...
wasm::to_js(val) //<--serialize result, even if error
```

What if deserializing a `Value` from the ptr fails? If so, we get a deserialization-error, which is serialized to our cljs caller as:

```clojure
[{:type "serde-fressian"
  :category "De" ;; <--deserialization error
  ...}]
```

What if deserialization fails, and then (stay with me) serializing the deserialization-error fails. Then you will get a serialization-error, which you can probably guess, is serialized too. But what if serializing the serialization-error fails? We can anticipate what would happen by taking a peek at the source for `serde_fressian::wasm::to_js`:

```rust
// from wasm/mod.rs
pub fn to_js<S: Serialize>(value: S) -> *mut u8
{
    let vec: Vec<u8> = ser::to_vec(&value).unwrap_or_else(|err| {
        let res: Result<(), error::Error> = Err(err);
        ser::to_vec(&res).unwrap()
    });
    bytes_to_js(vec)
}
```

Let's unpack the main bit:

```rust
let vec: Vec<u8> = // 1
  ser::to_vec(&value) // 2
      .unwrap_or_else(|err| { // 3
          let res: Result<(), error::Error> = Err(err); // 4
          ser::to_vec(&res).unwrap() // 5
      });
```
1. We are expecting to bind a byte vec
2. We try to serialize the value given to `to_js`, returning `Result<Vec<u8>,Error>`
3. `result.unwrap_or_else()` means we unwrap the result if it is ok (returning `Vec<u8>`), but if it is not ok (a serialization-error) we pass that error to a closure
4. Inside the closure, wrap the serialization-error in a result so that it will serialize as an Err, rather than just a value.
5. We serialize the `Err(err)` and no matter what call `result.unwrap()`.

Calling unwrap at the end means that we are assuming that serializing serialization-errors will always succeed and that it produces `Vec<u8>`. At this point you can safely rely on this not happening, but if it does, the author has let you down and you should raise an issue. If in actuality it fails, and a `Vec<u8>` doesn't arrive where it is expected, then that unwrap causes a [`Panic`][Panic]



#### dont panic
A panic is an unrecoverable fatal runtime error. Panics can come from inappropriately unwrapping Result and Option types, but also things such as failed assertions. In native rust it would kill the thread, but in WebAssembly it throws a [RuntimeError][Runtime] with a typically useless message.

Rust offers a way to catch panics by offering to call a `panic_hook` function. When a panic occurs, rust will call the hook with a description of the error. Serde-fressian is configured to catch the panic, serialize the description, and call an imported js function with a ptr to the serialized panic message. If `fress.wasm/call` catches a [RuntimeError][Runtime], it will read from the ptr and return an error map:

```clojure
[{:type :panic
  :value "...assertion failed at..."}]
```

### error handling in summary:

When working with fress you can expect wasm errors to fall into a few categories:
  1. serde-fressian errors
    - __most common__: you tried to deserialize a type not described by the bytecode it is given
      - ex: the data describes a map and you try to read a string
    - rare: serialization failed
      - ex: trying to fix a large u64 into a fressian int (i64)
  2. errors from 3rd party crates
    - cannot expect them to conform to serialization needs. You may have to wrap them in your own error types with custom serialize impls if you want to inspect them in js
    - ex: parsing a regex string into a Regex type returns a `Result<Regex, RegexError>`
  3. custom errors you define
    - see [custom_errors.md](custom_errors.md)
  4. panics
    - something has gone horribly wrong.
    - An assertion failed, a Result/Option was mishandled, or theres a bug somewhere

[serde-fressian]: https://github.com/pkpkpk/serde-fressian
[Result]: https://doc.rust-lang.org/std/result
[Panic]: https://doc.rust-lang.org/std/panic
[Runtime]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WebAssembly/RuntimeError


# Wasm Guide 1: The Result
Some key goals for fressian-wasm are to remove interop friction with rust and to provide pleasant development ergonomics. The way this is done is by infecting the clojurescript interface with Rust's obsession with first class error handling. In fressian-wasm, we want to take the rust [__Result__][Result] type and use it to ***propagate errors to javascript***.

### `Result<T,E>`
Instead of exceptions, Rust has the [`Result<T,E>`][Result] enum. Functions that expect to sometimes fail return a Result (io, serialization...). These functions return `Result::Ok(T)` if they succeed, and `Result::Err(E)` if they fail. The `Result` type is so pervasive in Rust that the std prelude includes globally the `Ok(T)` and `Err(E)` functions as shorthands for creating `Result` variants. This is how we will refer to them moving forward.

In cljs we model the `Result` type with a simple vector `[?err ?ok]`, where err is present when the operation fails, and nil when it succeeds.

If your wasm function fails or you want your return value to be recognized as an error, then you need to call `wasm::to_js` with `Err(E)`. When `serde_fressian::ser` sees an `Err(E)`, it will serialize `E` with an error code prefix. The `fress.wasm/read` function looks for this error code, and returns the next object as `[err]`. Any type given to `wasm::to_js` that is not a `Err(E)` will be written as is, even the `E` you are otherwise using as an error type. Errors are just values.


| given to `wasm::to_js` | cljs reads as...
|-----------------|------
|`Result::Err(E)` | `[E]`
|`E`              | `[nil E]`
|`Result::Ok(T)`  | `[nil T]`
|`T`              | `[nil T]`

This all comes with an important caveat: in order to serialize a `Result<T,E>`, both `T` and `E` must implement the `serde::ser::Serialize` trait. Rust will not let your code compile if this condition is not met. From the serde src:

```rust
// from serde/src/ser/impls.rs
impl<T, E> Serialize for Result<T, E>
where
    T: Serialize,
    E: Serialize,
{
  // ...
}
```

Serde has its own [data model][data-model], which includes `serde::ser::Serialize` and `serde::de::Deserialize` impls for all basic rust types and collections. How serde interacts with fressian will be the subject of [later guides][understanding_serde], but for now you can assume most basic rust types serialize into their obvious clojure counterparts, with some extra work required to utilize the full expressiveness of the fressian format. More immediately though, this means any error type `E` must implement `serde::ser::Serialize` if it is to be given to javascript. [Implementing your errors][custom_errors] deserves its own guide, but you can rely on serde_fressian to serialize its own errors.


 <hr>

## An example: Echo values across the wasm barrier

#### Rust...
```rust
use serde_fressian::error::{Error as FressError};
use serde_fressian::value::{Value};
use serde_fressian::wasm::{self};

#[no_mangle]
pub extern "C" fn echo(ptr: *mut u8, len: usize) -> *mut u8
{
    // read a value from javascript
    let res: Result<Value, FressError> = wasm::from_ptr(ptr, len);

    // from_ptr borrows, Value copies. So must own and free bytes separately.
    wasm::fress_dealloc(ptr, len);

    // serializes the result, hands ownership of resulting bytes over to js
    wasm::to_js(res)
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

(assert (= (echo "hello world!") [nil "hello world!"]))

(def data [{::sym 'foo/bar
            :inst (js/Date.)
            :set #{42 true nil "😎😎😎"}
            :bytes (js/Uint8Array. #js [0 1 2])}])

(assert (= (echo data) [nil data]))
```

### `fress.wasm/call`
The `fress.wasm/IFressWasmModule` protocol offers primitives for module interop but in practice all you need is `fress.wasm/call`. The above `(fress.wasm/call Mod "echo" any)` 'callstack' broken down:

| step | location| description
|------|---------|-----------|
| 1    |  cljs   | pass `fress.wasm/call` an object, it is converted into fressian bytes and written into wasm memory
| 2    |  cljs   | A pointer to those bytes (and their length) is passed to the exported `"echo"` function
| 3    |  wasm   | The `echo` fn is called with a ptr and length. It deserializes those bytes into the `serde_fressian::value::Value` enum (which can represent any fressian type)
| 4    |  wasm   | Rust `echo` takes the __Result__ of that deserialization and serializes it right back to javascript, returning a ptr
| 5    |  cljs   | `fress.wasm/call` receives that pointer internally, reads from it, and returns its content back to the caller

### `serde_fressian::wasm::from_ptr<'a,T>(ptr: *mut u8, len: usize) -> Result<T, FressError>`

This is the magic serde provides. `from_ptr` takes a ptr to some bytes and their length, and then attempts to __deserialize a type param `T`__ returning a `Result<T, serde_fressian::error::Error>`. From the echo example:

```rust
let res: Result<Value, FressError> = wasm::from_ptr(ptr, len);
//              ^^^^^ --> the type used by wasm::from_ptr to deserialize
```

 So here the type param is a `serde_fressian::value::Value` enum, and at compile time rust looks for a `deserialize` impl for `Value`.

The purpose of the `Value` enum is to accomodate any [supported][supported] fressian type, so you can expect this to reliably return `Ok(Value::TYPE(T))`, where `TYPE/T` is whatever fressian value you sent from cljs. In practice `Value` is less useful than a more specific type. For example, if you know you will receive a string:

```rust
let res: Result<String, FressError> = wasm::from_ptr(ptr, len);
```

+ Any exported functions you wish to receive values from cljs must always accept `(ptr: *mut u8, len: usize)` in their parameter signature.  Those values should be given to `wasm::from_ptr` or owned and read with `de::from_vec`

TODO: Copying, ownership





### `serde_fressian::wasm::to_js`
`to_js` is the inverse operation: it takes a value, serializes it into a fressian bytecode representation, and hands a ptr to those bytes off to javascript.

```rust
wasm::to_js(res) // serialize the result
```
+ Any exported functions you wish to transport values to js must always return `*mut u8` in its  return signature

+ These bytes are [forgotten][forgotten] by rust and are effectively owned by the javascript consumer. This means that slice of memory is unreachable to the wasm allocator. If ownership is not returned to rust, this becomes a leak. 

+ Once javascript is done reading the bytes, ownership can be returned to the rust allocator by calling `wasm::fress_dealloc` with the original ptr and length. `fress.wasm/read` and `fress.wasm/call` do this for you

<hr>


### serde-fressian errors

The echo `Value` example is serde following the data exactly as it is described, so you'd have to put in some effort to break it.  A much more likely problem will occur when you try to deserialize a more specific type:

```rust
let val: Result<Vec<String>, FressError> = wasm::from_ptr(ptr, len);
```
This time we are specifically expecting the bytes to contain a collection of strings. What if the bytes encode something else? If we try to read the wrong type, `wasm::from_ptr` will return `Err(E)` where `E` is a `serde_fressian::error::Error` with info about where the deserialization failed. We can serialize this error to cljs and it will read as:

```clojure
[{:type "serde-fressian"
  :category "De" ;; <--deserialization error
  ...}]
```

What if serializing a value fails? Then you will get a serialization-error. Serialization errors are rare, and are automatically serialized back to cljs. This is in contrast to deserialization-errors, which you can choose if you want to send them to cljs. In the echo example, we do not distinguish if deserialization failed or not, we just send the Result as is.


```clojure
[{:type "serde-fressian"
  :category "Ser" ;; <--serialization error
  ...}]
```

<hr>

### dont panic

But what if serializing the serialization-error fails? We can anticipate what would happen by taking a peek at the source for `serde_fressian::wasm::to_js`:

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
2. We try to serialize the value given to `to_js`, returning `Result<Vec<u8>, serde_fressian::error::Error>`
3. `result.unwrap_or_else()` means we unwrap the result if it is `Result::Ok(Vec<u8>)` (returning `Vec<u8>`), but if it is not ok (a serialization-error) we pass that error to a closure
4. Inside the closure, wrap the serialization-error in a result so that it will serialize as an `Result::Err(err)`, rather than just a value.
5. We serialize the `Err(err)` and no matter what call `result.unwrap()`.

Calling unwrap at the end means that we are assuming that serializing serialization-errors will always succeed and that it produces `Vec<u8>`. At this point you can safely rely on this being ok, but if it does fail, the author has let you down and you should raise an issue.

If in actuality it fails, and a `Vec<u8>` doesn't arrive where it is expected, then that unwrap causes a [__Panic__][Panic]. A panic is an unrecoverable fatal runtime error. Panics can come from inappropriately unwrapping Result and Option types, but also things such as failed assertions. In native rust it would kill the thread, but in WebAssembly it throws a [RuntimeError][Runtime] with a typically useless message.

Rust offers a way to catch panics by calling a `panic_hook` function. When a panic occurs, rust will call the hook with a description of the error. Serde-fressian is configured to catch the panic, serialize the description, and call an imported js function with a ptr to the serialized panic message. If `fress.wasm/call` catches a [RuntimeError][Runtime], it will read from the ptr and return an error map:

```clojure
[{:type :panic
  :value "...assertion failed at..."}]
```

<hr>

### error handling in summary:

When working with fress you can expect wasm errors to fall into a few categories:
  1. __serde-fressian errors__
     - most common: you tried to deserialize a type not described by the bytecode it is given
       - ex: the data describes a map and you try to read a string
     - rare: serialization failed
       - ex: trying to fix a large u64 into a fressian int (i64)
  2. __errors from 3rd party crates__
     - cannot expect them to conform to serialization needs. You may have to wrap them in your own error types with custom serialize impls if you want to inspect them in js
     - ex: parsing a regex string into a Regex type returns a `Result<Regex, RegexError>`
  3. __custom errors you define__
     - see [custom_errors][custom_errors]
  4. __panics__
     - something has gone horribly wrong.
     - An assertion failed, a Result/Option was mishandled, or theres a bug somewhere

[serde-fressian]: https://github.com/pkpkpk/serde-fressian
[wasm_mod]: https://github.com/pkpkpk/serde-fressian/blob/master/src/wasm/mod.rs
[fress.wasm]: https://github.com/pkpkpk/fress/blob/master/src/main/cljs/fress/wasm.cljs
[Result]: https://doc.rust-lang.org/std/result
[Panic]: https://doc.rust-lang.org/std/panic
[Runtime]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/WebAssembly/RuntimeError
[data-model]: https://serde.rs/data-model

[supported]: TODO
[custom_errors]: 2_serde_fressian.md
[understanding_serde]: 3_serde_fressian.md
[forgotten]: https://doc.rust-lang.org/std/mem/fn.forget.html

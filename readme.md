```clojure

com.github.pkpkpk/fress {:mvn/version "0.4.307"}

```
```clojure
[com.github.pkpkpk/fress "0.4.307"]
```

[![Clojars Project](https://img.shields.io/clojars/v/com.github.pkpkpk/fress.svg)](https://clojars.org/com.github.pkpkpk/fress)

## wasm⥪fressian⥭cljs

Read the introductory [blog post](https://pkpkpk.github.io/wasm%E2%A5%AAfressian%E2%A5%ADcljs.html)

Try the [quick-start](https://github.com/pkpkpk/fressian-wasm-quick-start)

When used in concert with [__serde-fressian__][serde-fressian], __fress__ can be used to convey rich values to and from WebAssembly modules. Serde-fressian is an implementation of fressian for [the Rust programming language][rustlang]. When compiled for WebAssembly, the [__`serde_fressian::wasm`__][sfwasm] module is designed to interface with the [__`fress.wasm`__][fress.wasm] namespace. Together they deliver a seamless transition from webassembly functions and their cljs consumers with minimal overhead. ***Fressian-wasm*** makes wasm functions feel like supercharged clojurescript functions.

There is a second companion library: [cargo-cljs](https://github.com/pkpkpk/cargo-cljs), a clojurescript library for scripting the rust build tool [cargo](https://doc.rust-lang.org/cargo/index.html) via nodejs.

Please refer to the [doc](doc) folder for wasm specific documentation.

The remainder of this readme below pertains to fressian for binary data unrelated to wasm usage. There are relevant details about the fressian encoding itself, but the api for reading and writing to wasm modules is completely different.


[serde-fressian]: https://github.com/pkpkpk/serde-fressian
[rustlang]: https://github.com/rust-lang/rust/
[fress.wasm]: https://github.com/pkpkpk/fress/blob/master/src/main/cljs/fress/wasm.cljs
[sfwasm]: https://github.com/pkpkpk/serde-fressian/blob/master/src/wasm/mod.rs

<hr>
(everything below this line is for binary serialization usage only)

## Quick Start

```clojure
(require '[fress.api :as fress])

(def buf (fress/byte-stream))

(def writer (fress/create-writer buf))

(def data [{::sym 'foo/bar
            :inst #?(:cljs (js/Date.)
                     :clj (java.util.Date.))
            :set #{42 true nil "string"}}])

(fress/write-object writer data)

(def reader (fress/create-reader buf))

(assert (= data (fress/read-object reader)))

```

<hr>


### Reading+Writing bytes with `fress.api/byte-stream`

In javascript, binary data is kept in [ArrayBuffers][4]. [TypedArrays][9] are simply [interpretive views][8] on array buffer instances. Both are fixed size and there are no streaming constructs. If you want to 'grow' a typed array, you need to allocate a new buffer and copy over the old buffer's contents. Doing so on every write is prohibitively slow, so `fress.api/byte-stream` addresses this by pushing bytes onto a plain javascript array which is realized into a [byte-array][5] only when deref'd or closed.

On the jvm, `fress.api/byte-stream` is the BytesOutputStream [provided by fressian][6] extended with `clojure.lang.IDeref`. Dereferencing returns a [java.nio.ByteBuffer][7] that realizes the current state of its stream.

+ `fress.api/create-reader` will automatically coerce byte-streams into a readable buffer
  - jvm too
  - useful for testing
+ byte-streams are stateful, no wrap behavior in js
  - If you deref a byte-stream and then continue to write, you will need to deref again to see the new bytes output in the buffer
+ `fress.api/create-writer` also accepts any typed-array or arraybuffer instance
  - you are responsible for making sure you have enough room to write.
+ in cljs, byte-streams can be recycled by calling `reset`



<hr>

### EOF
When a reader reaches the end of its buffer, it will throw a `(js/Error. "EOF")` (java.io.EOFException on JVM).
  + nil is a value, so gotta throw!
  + `fress.api/read-all` and `fress.api/read-batch` will handle this for you.

By default, footers in cljs readers will automatically trigger an EOF throw, preventing oob reads when there is excess room remaining. The intended use case is receiving a pointer on memory and simply reading off fressian bytes until whichever comes first: a natural EOF or a footer. You can avoid the conundrum by always writing single collections, but that is not always possible or desirable.

<hr>

### Convenience Functions

+ `fress.api/write<object, & opts> -> bytes`
  - takes any writable object (including a collection) and returns fressian bytes.
  - accepts same args as `create-writer` but writer + buffer creation are done for you
  - `:footer? true` option to automatically seal bytes off with footer
  - convenient when you have all data you want to write ahead of time.

+ `fress.api/read<readable, & opts> -> any`
  - takes bytes or bytestream and returns a single object read off the bytes
  - accepts same args as `create-reader` but reader creation is done for you

+ `fress.api/read-all<(readable|reader), & options> -> Vec<any>`
  - accepts reader, bytes, or bytestream, reads off everything it can. returns vector of contents
  - accepts same options as reader
  - automatically handles thrown EOFs for you

<hr>

### Extending with your own types

1. Decide on a string tag name for your type, and the number of fields it contains
2. define a *write-handler*, a `fn<writer, object>`
  + use `(fress/write-tag writer tag field-count)`
  + call `fress/write-object` on each component of your type
3. create a writer and pass a `:handler` map of `{type writeHandler}`
  - [`:handlers` passed to JVM writers have a different shape](#on-the-server)

Example: lets write a handler for javascript errors

``` clojure
(defn write-error [writer error]
  (let [name (.-name error)
        msg (.-message error)
        stack (.-stack error)]
    (fress/write-tag writer "js-error" 3) ;<-- don't forget field count!
    (fress/write-object writer name) ;<= implicit ordering, hmmm...
    (fress/write-object writer msg)
    (fress/write-object writer stack)))

(def e (js/Error "wat"))

(def writer (fress/create-writer out))

(fress/write-object writer e) ;=> throws, no handler!

(def writer (fress/create-writer out :handlers {js/Error write-error}))

(fress/write-object writer e) ;=> OK!
```

+ __Fress will automatically test if each written object is an instance of a registered type->write-handler pair.__ So write-error will also work for `js/TypeError`, `js/SyntaxError` etc

+ types that can share a writehandler but are not prototypically related can be made to share a write handler by passing them as seq in the handler entry key ie `(create-writer out :handlers {[typeA typeB] write-A-or-B})`

So now let's try reading our custom type:

```clojure
(def rdr (fress/create-reader out))

(def o (fress/read-object rdr))

(assert (instance? r/TaggedObject o))
```

So what happened? When the reader encounters a tag in the buffer, it looks for a registered read handler, and if it doesnt find one, its **uses the field count** to read off each component of the unidentified type and return them as a `TaggedObject`. `TaggedObject`s are generic containers for types a reader does not know how to handle. The field count is important because it lets consumers gracefully preserve the reading frame without forehand knowledge of whatever types you throw at it. Downstreams users do not have to care.

We can fix this by adding a read-error function:

```clojure
(defn read-error [reader tag field-count]
  (assert (= 3 field-count))
  {:name (fress/read-object reader) ; :name was first, right?
   :msg (fress/read-object reader)
   :stack (fress/read-object reader)
   :tag tag})

(def rdr (fress/create-reader out :handlers {"js-error" read-error}))

(fress/read-object reader) ;=> {:name "Error" :msg "wat" :stack ...}

```

Our write-error function chose to write each individual component sequentially, not as children of a parent list or, even better, as a map. This puts a burden on our read fn to both grab each individual field and *know the right order* of the components as they are read off. This will not be pleasant to maintain. A better solution would be to just write errors as maps and let fressian do the work for us.

``` clojure
(defn write-error [writer error]
  (fress/write-tag writer "js-error" 1)
  (fress/write-object writer
    {:name (.-name error)
     :msg (.-message error)
     :stack (.-stack error)}))

(defn read-error [reader tag field-count]
  (assoc (fress/read-object reader) :tag tag))
```

<hr>

### Fressian is just S-Expressions.

+ Fixed sized vectors, lists, and sequences are all written as generic lists and are read back as vectors.

+ Lists have three components: type, length, and body. Short lists have their length 'packed' with their type code. Longer lists are given a dedicated length segment. A list with length > 8 would be represented as:

```
LIST | length-n | item_0 | item_1 | ... | item_n-1
                     ^---can be own multi-byte reading frame
```

+ When you are in a situations where you have a sequence of indeterminant size or you need to write asynchronously, you can use `fress.api/begin-open-list` and `fress.api/begin-closed-list` to establish a list reading frame. Rather than rely on a known length, readers will encounter a 'open' signal and then call read-object continuously until a END_COLLECTION is seen or EOF is thrown.

```
BEGIN_CLOSED_LIST | value | value | value | END_COLLECTION
```

+ The difference between `begin-closed-list` and `begin-open-list` is that EOF is an acceptable ending for an open list and will be handled for you. Closed lists expect a END_COLLECTION signal and will throw EOF as normal if encountered prematurely.

+ Many structs are written as variants of 'list' with the differences being their tag and the way read handlers interpret their contents. For example:
  - A set is simply a SET code followed by a list
  - A map is a MAP code followed by list of k-v pairs. So the bytecode for a map with 3 entries could look something like:

```
MAP | BEGIN_CLOSED_LIST | k | v | k | v | k | v | END_COLLECTION
```

<hr>

### Records
clojure.data.fressian can use defrecord constructors to produce symbolic tags (.. its class name) for serialization, and use those same symbolic tags to resolve constructors during deserialization. In cljs, symbols are munged in advanced builds, and we have no runtime resolve. How do we deal with this?

1. When writing records, include a `:record->name` map at writer creation
  - ex: `{RecordConstructor "app.core/RecordConstructor"}`
  - the string name should be the same as the string of the fully resolved symbol, and is used to generate a symbolic tag representing its className
2. When reading records, include `:name->map-ctor` map at reader creation
  - ex: `{"app.core/RecordConstructor" map->RecordConstructor}`
  - Why the record map constructor? Because clojure.data.fressian's default record writer writes record contents as maps
  - if the name is not recognized, it will be read as a TaggedObject containing all the fields defined by the writer.

``` clojure
(require '[fress.api :as fress])

(defrecord SomeRecord [f1 f2]) ; map->SomeRecord is now implicitly defined

(def rec (SomeRecord. "field1" "field2"))

(def buf (fress/byte-stream))

(def writer (fress/create-writer buf :record->name {SomeRecord "myapp.core.SomeRecord"}))

(fress/write-object writer rec)

(def reader (fress/create-reader buf :name->map-ctor {"myapp.core.SomeRecord" map->SomeRecord}))

(assert (= rec (fress/read-object reader)))
```

+ in clojurescript you can override the default record writer by adding a `"record"` entry in `:handlers`. A built in use case for this is `fress.api/field-caching-writer` which offers a way to automatically cache the value of keys that pass a predicate

```clojure
(fress/create-writer buf :handlers {"record" (fress/field-caching-writer #{:f1})})
```

+ on the jvm:

```clojure
(let [cache-writer (fress/field-caching-writer #{:f1})]
  (fress/create-writer buf :handlers
    {clojure.lang.IRecord {"clojure/record" cache-writer}}))
```

<hr>

### Raw UTF-8

Fressian compresses UTF-8 strings when writing them. This means a reader must decompress each char to reassemble the string. If payload size is your primary concern this is great, but if you want faster read+write times there is another option. The javascript [TextEncoder][1] / [TextDecoder][2] API has [growing support][3] (also see analog in node util module) and is written in native code. TextEncoder will convert a javascript string into plain utf-8 bytes, and the TextDecoder can reassemble a javascript string from raw bytes faster than javascript can assemble a string from compressed bytes.

By default fress writes strings using the default fressian compression. If you'd like to write raw UTF-8, you can use `fress.api/write-utf8` on a string, or bind  `fress.writer/*write-raw-utf8*` to `true` before writing. If you are targeting a jvm reader, you must also bind `*write-utf8-tag*` to `true` so the tag is picked up by the jvm reader. Otherwise a code is used that is only present in fress clients.

<hr>

### Caching

`write-object` has a second arity that accepts a boolean `cache?` parameter. The first time this is called on value, a 'cache-code' is assigned to that object which signals the reader to associated that code with the object. Subsequent writes of an identical object will just be written as that code and the reader will interpret it and return the same value.
  - Readers can only interpret these cache codes in the context in which the were established. A naive reader who picks up reading bytes after a cache signal is sent will simpy return integers and not the appropriate value
  - Writers can signal readers to reset their cache with a call to reset-caches. You are free to have multiple cache contexts within the same bytestream

<hr>

### checksum
Writers maintain a checksum of every byte written, and include this (with a byte-count) inside a footer. By default readers ignore this, but you can pass `:checksum? true` when creating a reader to validate the checksum when a footer is read. An invalid checksum will throw.

<hr>

### On the Server
Fress wraps clojure.data.fressian and can be used as a drop in replacement.

+ read-handlers are automatically wrapped in fressian lookups; just pass a map of `{tag fn<rdr,tag,field-count>}`, same as you would for cljs
+ write-handlers are also automatically wrapped as lookups, but **the shape for handler args is different**! It must be `{type {tag fn<writer, obj>}`

```clojure
(fress/create-writer out :handlers {MyType {"mytype" (fn [writer obj] ...)}})
```

+ if you are already reifying fressian read+writeHandlers, they will be passed through as is

#### Differences from clojure.data.fressian
  + CLJS has no support for BigDecimal & Ratios at this time

<hr>

## Type Support

fressian type | cljs-read | cljs-write | note
--------------|-----------|------------|-------
int           | :white_check_mark:  |  :white_check_mark: | `fress.reader/*throw-on-unsafe?*` defaults to `true`
bool          | :white_check_mark:  |  :white_check_mark:
bytes         | :white_check_mark:  |  :white_check_mark: | Int8Array
float         | :white_check_mark:  |  :white_check_mark:
double        | :white_check_mark:  |  :white_check_mark:
string        | :white_check_mark:  |  :white_check_mark:
null          | :white_check_mark:  |  :white_check_mark:
list          | :white_check_mark:  |  :white_check_mark:
boolean[]     | :white_check_mark:  |  :white_check_mark: | use `(write-as wrt "boolean[]" val)`
int[]         | :white_check_mark:  |  :white_check_mark: | Int32Array
long[]        | :white_check_mark:  |  :white_check_mark: | BigInt64Array or `(write-as wrt "long[]" val)`
float[]       | :white_check_mark:  |  :white_check_mark: | Float32Array
double[]      | :white_check_mark:  |  :white_check_mark: | Float64Array
Object[]      | :white_check_mark:  |  :white_check_mark: | use `(write-as wrt "Object[]" val)`
map           | :white_check_mark:  |  :white_check_mark:
set           | :white_check_mark:  |  :white_check_mark:
uuid          | :white_check_mark:  |  :white_check_mark:
regex         | :white_check_mark:  |  :white_check_mark:
inst          | :white_check_mark:  |  :white_check_mark:
uri           | :white_check_mark:  |  :white_check_mark: | [goog.Uri](https://google.github.io/closure-library/api/goog.Uri.html)
records       | :white_check_mark:  |  :white_check_mark: | see usage details in README
bigint        | :white_check_mark:  |  :white_check_mark:
sym           | :white_check_mark:  |  :white_check_mark:
key           | :white_check_mark:  |  :white_check_mark:
char          | :white_check_mark:  |  :white_check_mark: | use `(write-as wrt "char" \a)`
ratio         | :x:  |  :x: | TODO
bigdec        | :x:  |  :x: | TODO

### Further Reading
+ https://github.com/clojure/data.fressian/wiki
+ https://github.com/Datomic/fressian/wiki
+ https://youtu.be/JArZqMqsaB0



[1]: https://developer.mozilla.org/en-US/docs/Web/API/TextEncoder
[2]: https://developer.mozilla.org/en-US/docs/Web/API/TextDecoder
[3]: https://caniuse.com/#feat=textencoder
[4]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/ArrayBuffer
[5]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Int8Array
[6]: https://github.com/Datomic/fressian/blob/master/src/org/fressian/impl/BytesOutputStream.java
[7]: https://docs.oracle.com/javase/7/docs/api/java/nio/ByteBuffer.html
[8]: https://hacks.mozilla.org/2017/06/a-cartoon-intro-to-arraybuffers-and-sharedarraybuffers/
[9]: https://developer.mozilla.org/en-US/docs/Web/JavaScript/Typed_arrays

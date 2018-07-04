# ~~`[fress "0.1.0"]`~~
+ ~~`{fress "0.1.0"}`~~


## Quick Start

```clojure
(require '[fress.api :as fress])

(def buf (fress/byte-stream))

(def writer (fress/create-writer buf))

(def data [{::key 'foo/bar :inst (js/Date.now)} #{42 true nil "string"}])

(fress/write-object writer data)

(def reader (fress/create-reader buf))

(assert (= data (fress/read-object reader)))

```

<hr>

### Read & Writing Bytes in javascript
+ javascript array buffer limitations
+ nil is a value, so gotta throw!
+ excessive memory
  - write single objects
  - footers?
+ byte-stream
  - calling reader realizes current state, writing again invalidates
  - flushTo
  - currently lacks wrap functionality
+ checksum

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


+ `fress.api/read-batch<reader> -> Vec<any>`
  - takes an existing reader and reads off everything it can, return a vector of its contents
  - automatically handles thrown EOFs for you


+ `fress.api/read-all<(readable|reader), & options> -> Vec<any>`
  - accepts reader, bytes, or bytestream, returns vector of contents
  - accepts same options as reader

<hr>

### Extending with your own types

1. Decide on a string tag name for your type, and the number of fields it contains
+ define a __write-handler__, a `fn<writer, object>`
  + use `(w/writeTag writer tag field-count)`
  + call writeObject on each field component
    + each field itself can be a custom type with its own tag + fields
+ create a writer and pass a `:handler` map of `{type writeHandler}`
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

+ types that can share a writehandler but are not prototypically related can be made to share a write handler by passing them as seq in the handler entry key ie `(create-writer out :handlers {[typeA typeB] writer})`

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

```clojure
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

### Lists

+ Fixed sized vectors, lists, and sequences are all written as generic \`list\`s, and are read back as vectors. A list with length > 8 would be represented as:

```
LIST | length-n | item_0 | item_1 | ... | item_n
                     ^---can be own multi-byte reading frame
```

+ When you are in a situations where you have a sequence of indeterminant size or you need to write asynchronously, you can use `fress.api/begin-open-list` and `fress.api/begin-closed-list` to establish a list reading frame. Rather than rely on an item count, readers will encounter and open signal and then and read off objects until a END_COLLECTION or EOF is seen.

```
BEGIN_CLOSED_LIST | value | value | value | END_COLLECTION
```

+ The difference between `begin-closed-list` and `begin-open-list` is that EOF is an acceptable ending for an open list and will be handled for you. Closed lists expect a END_COLLECTION signal and will throw EOF as normal if encountered prematurely.

+ Other compound data structs are all written as variants of 'list' with their differences being their tag header and the way read handlers interpret their contents
  - a Set is identical to a list except it is preceded by a 'SET' tag
  - A map is a list of k-v pairs preceded by a 'MAP' tag. So a map with 3 entries could look something like:

` MAP | BEGIN_CLOSED_LIST | k | v | k | v | k | v | END_COLLECTION`


<hr>

### Records
clojure.data.fressian can use defrecord constructors to produce symbolic tags (.. its class name) for serialization, and use those same symbolic tags to resolve constructors during deserialization. In cljs, symbols are munged in advanced builds, and we have no runtime resolve. How do we deal with this?

1. When writing records, include a `:record->name` map at writer creation
  - ex: `{RecordConstructor "app.core/RecordConstructor"}`
  - the string name should be the same as the string of the fully resolved symbol, and is used to generate a symbolic tag representing its className
2. When reading records, include `:name->map-ctor` map at reader creation
  - ex: `{"app.core/RecordConstructor" map->RecordConstructor}`
  - Why the record map constructor? Because clojure.data.fressian's default record writer writes record contents as maps
  - if the name is not recognized, it will be read as a TaggedObject containing all the fields defined by the writer (more on that later).

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

JVM fressian compresses UTF-8 strings when writing them. This means a reader must decompress each char to reassemble the string. If payload size is your primary concern this is great, but if you want faster read+write times there is another option. The javascript [TextEncoder][1] / [TextDecoder][2] API has [growing support][3] (also see analog in node util module) and is written in native code. TextEncoder will convert a javascript string into plain utf-8 bytes, and the TextDecoder can reassemble a javascript string from raw bytes faster than javascript can assemble a string from compressed bytes.

By default fress writes strings using the default fressian compression. If you'd like to write raw UTF-8, you can use `fress.api/write-utf8` on a string, or bind  `fress.writer/*write-raw-utf8*` to `true` before writing. If you are targeting a jvm reader, you must also bind `*write-utf8-tag*` to `true` so the tag is picked up by the jvm reader. Otherwise a code is used that is only present in fress clients.

<hr>

### Caching

`write-object` has a second arity that accepts a boolean `cache?` parameter. The first time this is called on value, a 'cache-code' is assigned to that object which signals the reader to associated that code with the object. Subsequent writes of an identical object will just be written as that code and the reader will interpret it and return the same value.
  - Readers can only interpret these cache codes in the context in which the were established. A naive reader who picks up reading bytes after a cache signal is sent will simpy return integers and not the appropriate value
  - Writers can signal readers to reset their cache with a call to reset-caches. You are free to have multiple cache contexts within the same bytestream

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
  + CLJS has no support for BigInteger, BigDecimal, chars, ratios at this time

<hr>

### Further Reading
+ https://github.com/clojure/data.fressian/wiki
+ https://github.com/Datomic/fressian/wiki
+ https://youtu.be/JArZqMqsaB0



[1]: https://developer.mozilla.org/en-US/docs/Web/API/TextEncoder
[2]: https://developer.mozilla.org/en-US/docs/Web/API/TextDecoder
[3]: https://caniuse.com/#feat=textencoder
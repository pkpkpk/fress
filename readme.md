
# WIP

## Quick Start



### Differences from clojure.data.fressian
  + no BigInteger, BigDecimal, chars, ratios at this time
  + EOF

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

(def rec (SomeRecord. "field1" ...))

(def buf (fress/streaming-writer))

(def writer (fress/create-writer buf :record->name {SomeRecord "myapp.core.SomeRecord"}))

(fress/write-object writer rec)

(def reader (fress/create-reader buf :name->map-ctor {"myapp.core.SomeRecord" map->SomeRecord}))

(assert (= rec (fress/read-object reader)))
```

You can override the default record writer by adding a `"record"` entry in `:handlers`. A built in use case for this is `fress.api/field-caching-writer` which offers a way to automatically cache values that pass a predicate

```clojure
(fress/create-writer buf :handlers {"record" (fress/field-caching-writer #{"some repetitive value"})})
```

<hr>

### Extending with your own types
  1. Decide on a string tag name for your type, and the number of fields it contains
  + define a __write-handler__, a `fn<writer, object>`
    + use `(w/writeTag writer tag field-count)`
    + call writeObject on each field component
      + each field itself can be a custom type with its own tag + fields
  + create a writer and pass a `:handler` map of `{type writeHandler}`


Example: lets write a handler for javascript errors

``` clojure
(require '[fress.writer :as w])

(defn write-error [writer error]
  (let [name (.-name error)
        msg (.-message error)
        stack (.-stack error)]
    (w/writeTag writer "js-error" 3) ;<-- don't forget field count!
    (w/writeObject writer name)
    (w/writeObject writer msg)
    (w/writeObject writer stack)))

(def e (js/Error "wat"))

(def writer (fress/create-writer out))

(fress/write-object writer e) ;=> throws, no handler!

(def writer (fress/create-writer out :handlers {js/Error write-error}))

(fress/write-object writer e) ;=> OK!
```

Fress will automatically test if each written object is an instance of a registered write handler, so this will also work for `js/TypeError`, `js/SyntaxError` etc.

So now let's try reading our custom type:

```clojure
(def rdr (fress/create-reader out))

(def o (fress/read-object rdr))

(assert (instance? r/TaggedObject o))
```

So what happened? When the reader encounters a tag in the buffer, it looks for a registered read handler, and if it doesnt find one, its **uses the field count** to read off each component of the unidentified type and return them as a `TaggedObject`. The field count is important because it lets consumers preserve the reading frame without forehand knowledge of whatever types you throw at it. Downstreams users do not have to care.

We can fix this by adding a read-error function:

```clojure
(defn read-error [reader tag field-count]
  {:name (r/readObject reader)
   :msg (r/readObject reader)
   :stack (r/readObject reader)})

(def rdr (r/reader out :handlers {"js-error" read-error}))

(r/readObject rdr) ;=> {:name "Error" :msg "wat" :stack ...}

```

<hr>

### Raw UTF-8

JVM fressian compresses UTF-8 strings when writing them. This means a reader must decompress each char to reassemble the string. If payload size is your primary concern this is great, but if you want faster read+write times there is another option. The javascript [TextEncoder][1] / [TextDecoder][2] API has [growing support][3] (also see analog in node util module) and is written in native code. TextEncoder will convert a javascript string into plain utf-8 bytes, and the TextDecoder can reassemble a javascript string from raw bytes faster than javascript can assemble a string from compressed bytes.

By default fress writes strings using the default fressian compression. If you'd like to write raw UTF-8, you can use `fress.api/write-utf8` on a string, or bind  `fress.writer/*write-raw-utf8*` to `true` before writing. If you are targeting a jvm reader, you must also pass a `true` second arg  (or bind `*write-utf8-tag*` to `true`) so the tag is picked up by the jvm reader. Otherwise a code is used that is only present in fress clients.

## Caching

 write without
 get size

 write with
 get size


[1]: https://developer.mozilla.org/en-US/docs/Web/API/TextEncoder
[2]: https://developer.mozilla.org/en-US/docs/Web/API/TextDecoder
[3]: https://caniuse.com/#feat=textencoder
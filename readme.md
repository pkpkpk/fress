
# WIP

## Quick Start

### Differences from clojure.data.fressian
  + no BigInteger, BigDecimal, chars, ratios at this time
  + EOF

<hr>

### Records
clojure.data.fressian can use defrecord constructors to produce symbolic tags for serialization, and use those same symbolic tags to resolve constructors during deserialization. In cljs, symbols are munged in advanced builds, and we have no runtime resolve. How do we deal with this?

 1. When writing records, we need to help fress by providing __a map linking a record constructor to a string-name__ which allows us to generate these symbolic tags.
 2. When reading records, we need to help fress by binding `fress.reader/*record-name->map-ctor*` to  __a map linking a string-name to a map->record fn__. These fns are created automatically for each defrecord.


``` clojure
(defrecord SomeRecord [f1 f2]) ; map->SomeRecord is now implicitly defined

(binding [w/*record->name* {SomeRecord "myapp.core.SomeRecord"}]
  (w/writeObject writer (SomeRecord. "field1" ...)))

(binding [r/*record-name->map-ctor* {"myapp.core.SomeRecord" map->SomeRecord}]
  (r/readObject reader))
```

If you read a record type that is not defined in the client, the reader will return a TaggedObject containing all the fields defined by the writer.

<hr>

### Extending with your own types
  1. Decide on a string tag name for your type, and the number of fields it contains
  + define a write-handler, a `fn<writer, object>`
    + use `(w/writeTag writer tag field-count)`
    + call writeObject on each field component
      + each field itself can be a custom type with its own tag + fields
  + create a writer and pass a map of `{type writeHandler}`


Example: lets write a handler for javascript errors

``` clojure
(defn write-error [writer error]
  (let [name (.-name error)
        msg (.-message error)
        stack (.-stack error)]
    (w/writeTag writer "js-error" 3) ;<-- don't forget field count!
    (w/writeObject writer name)
    (w/writeObject writer msg)
    (w/writeObject writer stack)))

(def e (js/Error "wat"))

(def writer (w/writer out))

(w/writeObject writer e) ;=> throws, no handler!

(def writer (w/writer out :handlers {js/Error write-error}))

(w/writeObject writer e) ;=> OK!
```
So this works for errors created with `js/Error`, but what about `js/TypeError`, `js/SyntaxError` ...? We can use the same custom writer for multiple related types by using a collection of those types in our handler argument during writer creation.

```clojure
(def writer (w/writer out :handlers {[js/Error js/TypeError js/SyntaxError]  write-error}))
```

So now let's try reading...

```clojure
(def rdr (r/reader out))

(def o (r/readObject rdr))

(assert (instance? r/TaggedObject o))
```

So what happened? When the reader encounters a tag in the buffer, it looks for a registered read handler, and if it doesnt find one, its **uses the field count** to read off each component of the unidentified type and return them as a `TaggedObject`. The field count is important because it lets consumers preserve the reading frame without forehand knowledge of whatever types you throw at it. Downstreams users do not have to care, but we do so lets add a read-error function

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

JVM fressian compresses UTF-8 strings when writing them. This means a reader must decompress each char to reassemble the string. If payload size is your primary concern this is great, but if you want faster read+write times there is another option. The javascript [TextDecoder/TextEncoder][1] API has [growing support][2] and is written in native code. TextEncoder will convert a javascript string into plain utf-8 bytes, and the TextDecoder can assemble a javascript string from raw bytes faster than javascript can assemble a string from compressed bytes.

By default fress writes strings using the default fressian compression. If you'd like to write raw UTF-8, bind  `fress.writer/*write-raw-utf8*` to `true` before writing the string. If you are targeting a jvm reader, you must also bind `*write-utf8-tag*` to `true` so the tag is picked up by the reader. Otherwise a code is used that is only present in fress clients.





[1]: https://developer.mozilla.org/en-US/docs/Web/API/TextDecoder
[2]: https://caniuse.com/#feat=textencoder
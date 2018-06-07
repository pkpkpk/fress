
# WIP

;; rundown all major types
records
keywords



## Differences
  + UTF8
  + EOF
  + Records
   - clojure.data.fressian can use defrecord constructors to produce symbolic tags for serialization, and use those same symbolic tags to resolve constructors during deserialization. In cljs, symbols are munged in advanced builds, and we have no runtime resolve. How do we deal with this?
     1. When writing records, we need to help fress by providing __a map linking a record constructor to a string-name__ generate these symbolic tags.
     2. When reading records, we need to help fress by providing __a map linking a string-name to a map->record fn__

``` clojure

(defrecord SomeRecord [f1 f2])

...

(binding [w/*record->name* {SomeRecord "myapp.core.SomeRecord"}]
  (w/writeObject writer (SomeRecord. "field1" ...)))

(binding [r/*record-name->map-ctor* {"myapp.core.SomeRecord" map->SomeRecord}]
  (r/readObject reader))


```
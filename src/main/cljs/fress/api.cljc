(ns fress.api
  #?(:clj (:refer-clojure :exclude (read)))
  #?(:cljs
     (:require [fress.reader :as r]
               [fress.writer :as w]
               [fress.impl.buffer :as buf])
     :clj
     (:require [clojure.data.fressian :as fressian]))
  #?(:clj (:import [org.fressian.handlers WriteHandler ReadHandler]
                   [org.fressian FressianWriter StreamingWriter FressianReader TaggedObject Writer Reader]
                   [java.io InputStream OutputStream])))

(set! *warn-on-reflection* true)

#?(:clj
   (defn private-field [obj fn-name-string]
     (let [m (.. obj getClass (getDeclaredField fn-name-string))]
       (. m (setAccessible true))
       (. m (get obj)))))

#?(:clj
   (defn- w->raw [wrt] (private-field wrt "rawOut")))

#?(:clj
   (defn- rdr->raw [rdr] (private-field rdr "is")))

#?(:clj
   (deftype utf8 [s]))

#?(:clj
   (defn utf8? [o] (instance? utf8 o)))

#?(:clj
   (def ^:dynamic *write-utf8-tag* false))

#?(:clj
   (def utf8-writer
     (reify WriteHandler
       (write [_ w u]
              (let [bytes (.getBytes (.-s u) "UTF-8")
                    raw-out (w->raw w)
                    length (count bytes)]
                (if *write-utf8-tag* ;<= client can read either
                  (.writeTag w "utf8" 2)
                  (.writeCode w (int 191)))
                (.writeCount w length)
                (.writeRawBytes raw-out bytes 0 length))))))

#?(:clj
   (def utf8-reader
     ;; cant modify fressian.impl.Codes so using code from client will fail
     ;; will not recognized without "utf8" tag, see writer
     ;; client will need to write with tag when targeting JVM
     (reify ReadHandler
       (read [_ rdr tag component-count]
             (let [length (int (.readInt rdr))
                   offset (int 0)
                   bytes (byte-array length)
                   raw-in (rdr->raw rdr)]
               (.readFully raw-in bytes offset length)
               (String. bytes "UTF-8"))))))

#?(:clj
   (defn read-handlers
     ([] (read-handlers nil))
     ([user-handlers]
      (let [default-handlers (assoc fressian/clojure-read-handlers "utf8" utf8-reader)
            handlers (merge default-handlers user-handlers)]
        (fressian/associative-lookup handlers)))))

#?(:clj
   (defn write-handlers
     ([](write-handlers nil))
     ([user-handlers]
      (let [default-handlers (assoc fressian/clojure-write-handlers utf8 {"utf8" utf8-writer})
            handlers (merge default-handlers user-handlers)]
        (-> handlers
            fressian/associative-lookup
            fressian/inheritance-lookup)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-reader
  "Create a fressian reader targeting in.
   - :handlers is just a map of tag->fn merged with default read handlers
   - :checksum? checks adler validity on each read, throws when fails
   - cljs allows reading from :offset"
  [^InputStream in & {:keys [handlers] :as opts}]
  #?(:clj
     (apply fressian/create-reader in (assoc opts :handlers (read-handlers handlers)))
     :cljs
     (apply r/reader in opts)))

#?(:cljs
   (def ^{:dynamic true
          :doc "map of record names to map->Record constructors at runtime"}
     *record-name->map-ctor* {})) ;; {"string-name" map->some-record}

(defn read-object
  "Read a single object from a fressian reader."
  [rdr]
  #?(:clj (fressian/read-object rdr)
     :cljs
     (binding [r/*record-name->map-ctor* *record-name->map-ctor*]
       (r/readObject rdr))))

(defn tagged-object?
  "Returns true if o is a tagged object, which will occur when
   the reader does not recognized a specific type.  Use tag
   and tagged-value to access the contents of a tagged-object."
  [o]
  #?(:clj (fressian/tagged-object? o)
     :cljs (instance? r/TaggedObject o)))

(defn tag
  "Returns the tag if object is a tagged-object, else nil."
  [o]
  #?(:clj (fressian/tag o)
     :cljs (get o :tag)))

(defn tagged-value
  "Returns the value (an Object array) wrapped by obj, or nil
   if obj is not a tagged object."
  [o]
  #?(:clj (fressian/tagged-value o)
     :cljs (get o :value)))

(defn create-writer
  "Create a fressian writer targeting out.
     - :handlers is just a map of {type {'tag' write-fn}} merged with default write handlers"
  [^OutputStream out & {:keys [handlers] :as opts}]
  #?(:clj (apply fressian/create-writer out (assoc opts :handlers (write-handlers handlers)))
     :cljs (apply w/writer out opts)))

#?(:cljs
   (def ^{:dynamic true
          :doc "map record-types -> string name desired for serializing records"}
     *record->name* {}))

(defn write-object
  "Write a single object to a fressian writer." ;<= ret?
  [wrt o]
  #?(:clj (fressian/write-object wrt o)
     :cljs
     (binding [w/*record->name* *record->name*]
       (w/writeObject wrt o))))

(defn write-utf8
  "write a string as raw utf-8 bytes"
  ([wrt s](write-utf8 wrt s false))
  ([wrt s tag?]
   (assert (string? s))
   #?(:clj (binding [*write-utf8-tag* tag?]
             (write-object wrt (utf8. s)))
      :cljs
      (binding [w/*write-raw-utf8* true
                w/*write-utf8-tag* tag?]
        (w/writeString wrt s)))))

(defn write-footer
  [writer]
  #?(:clj (fressian/write-footer writer)
     :cljs (w/writeFooter writer)))

(defn begin-closed-list
  "Begin writing a fressianed list.  To end the list, call end-list.
   Used to write sequential data whose size is not known in advance."
  [writer]
  #?(:clj (fressian/begin-closed-list writer)
     :cljs (w/beginClosedList writer)))

(defn end-list
  "Ends a list begun with begin-closed-list."
  [writer]
  #?(:clj (fressian/end-list writer)
     :cljs (w/endList writer)))

(defn begin-open-list
  "Writes fressian code to begin an open list.  An
   open list can be terminated either by a call to end-list,
   or by simply closing the stream.  Used to write sequential
   data whose size is not known in advance, in contexts where
   stream failure can safely be interpreted as end of list."
  [writer]
  #?(:clj (fressian/begin-open-list writer)
     :cljs (w/beginOpenList writer)))

(defn field-caching-writer
  "Returns a record writer that caches values for keys
   matching cache-pred, which is typically specified
   as a set, e.g. (field-caching-writer #{:color}).
   CLJS requires binding fress.api/*record->name* to {type 'record-name'}"
  [cache-pred]
  #?(:clj (fressian/field-caching-writer cache-pred)
     :cljs
     (fn [_ w rec]
       (binding [w/*record->name* *record->name*]
         (w/writeTag w "record" 2)
         (w/writeObject w (w/class-sym rec) true)
         (w/writeTag w "map" 1)
         (w/beginClosedList w)
         (reduce-kv ;; <= reduce-kv has a bug, needs test<<<<<<<<<<<<<<<<<<<<<<<
          (fn [^Writer w k v]
            (w/writeObject w k true)
            (w/writeObject w v (boolean (cache-pred k))))
          w
          rec)
         (w/endList w)))))

#?(:cljs
   (defn streaming-writer []
     (let [bytesWritten 0
           open? true
           buffer nil]
       (buf/StreamingWriter. #js[] bytesWritten open? buffer))))

#?(:cljs
   (defn flush-to
     ([stream out](flush-to stream out 0))
     ([stream out offset]
      (assert (instance? buf/StreamingWriter stream))
      (assert (some? (.-buffer out)))
      (buf/flushTo stream out offset))))

#_(:cljs
   (defn wrap
     ([stream out])
     ([stream out offset])))

(defn read
  "Convenience method for reading a single fressian object.
   Takes same options as create-reader"
  [readable & options]
  #?(:clj (apply fressian/read readable options)
     :cljs (r/readObject (apply create-reader readable options))))

(defn read-batch
  "Read a fressian reader fully (until eof), returning a (possibly empty)
   vector of results."
  [^Reader fin]
  (let [sentinel (Object.)]
    (loop [objects (transient [])]
      (let [obj #?(:clj (try (.readObject fin) (catch EOFException e sentinel))
                   :cljs (try (r/readObject fin) (catch js/Error e sentinel)))]
        (if (= obj sentinel)
          (persistent! objects)
          (recur (conj! objects obj)))))))

(defn write
  "Convenience method for writing a single object.  Returns a
   byte buffer.  Options are the same as for create-reader,
   with one additional option.  If footer? is specified, will
   write a fressian footer after writing the object."
  [obj & options]
  #?(:clj (apply fressian/write obj options)
     :cljs
     (let [{:keys [footer?]} (when options (apply hash-map options))
           bos (buf/streaming-writer)
           writer (apply create-writer bos options)]
       (w/writeObject writer obj)
       (when footer?
         (w/writeFooter writer))
       (buf/close bos))))




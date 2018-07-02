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
                   [org.fressian.impl RawOutput RawInput BytesOutputStream]
                   java.nio.ByteBuffer
                   [java.io InputStream OutputStream EOFException])))

(set! *warn-on-reflection* true)

#?(:clj
   (defn private-field [^Object obj name-string]
     (let [m (. (.getClass obj)(getDeclaredField name-string))]
       (. m (setAccessible true))
       (. m (get obj)))))

#?(:clj
   (defn- w->raw [wrt] (private-field wrt "rawOut")))

#?(:clj
   (defn- rdr->raw [rdr] (private-field rdr "is")))

#?(:clj
   (deftype utf8 [^String s]))

#?(:clj
   (defn utf8? [o] (instance? utf8 o)))

(def ^:dynamic *write-utf8-tag* false)

#?(:clj
   (defn fn->write-handler [f]
     (if (instance? org.fressian.handlers.WriteHandler f)
       f
       (reify WriteHandler
         (write [_ writer obj]
                (try
                  (f ^Writer writer obj)
                  (catch clojure.lang.ArityException e
                    (throw (Exception. "fressian write-handlers need to be fn<writer,obj>")))))))))

#?(:clj
   (defn fn->read-handler [f]
     (if (instance? org.fressian.handlers.ReadHandler f)
       f
       (reify ReadHandler
         (read [_ rdr tag field-count]
               (try
                 (f ^Reader rdr ^String tag field-count)
                 (catch clojure.lang.ArityException e
                   (throw (Exception. "fressian read-handlers need to be fn<reader,tag,field-count>")))))))))

#?(:clj
   (defn utf8-writer [w u]
     (let [s (.-s ^utf8 u)
           bytes (.getBytes ^String s "UTF-8")
           raw-out (w->raw w)
           length (count bytes)]
       (if *write-utf8-tag* ;<= client can read either
         (.writeTag ^FressianWriter w "utf8" 2)
         (.writeCode ^FressianWriter w (int 191)))
       (.writeCount ^FressianWriter w length)
       ;FIXME use writeBytes, code makes tagged-object compatible
       (.writeRawBytes ^RawOutput raw-out bytes 0 length))))


#?(:clj
   (defn utf8-reader
     "cant modify fressian.impl.Codes so using code from client will fail
      JVM readers must use \"utf8\" tag."
     [^Reader rdr tag _]
     (let [length (int (.readInt rdr))
           offset (int 0)
           bytes (byte-array length)
           raw-in (rdr->raw rdr)]
       (.readFully ^RawInput raw-in bytes offset length)
       (String. bytes "UTF-8"))))

#?(:clj
   (defn read-handlers
     "merge in user handlers with default-handlers, wrap for fressian lookup"
     ([] (read-handlers nil))
     ([user-handlers]
      (when user-handlers
        (assert
         (and (map? user-handlers)
              (every? string? (keys user-handlers))
              (every? fn? (vals user-handlers)))))
      (let [user-handlers (merge {"utf8" utf8-reader} user-handlers)
            handlers (into fressian/clojure-read-handlers
                           (map
                            (fn [[tag f]]
                              [tag (fn->read-handler f)]))
                           user-handlers)]
        (fressian/associative-lookup handlers)))))

#?(:clj
   (defn write-handlers
     "merge in user handlers with default-handlers, wrap for fressian lookup"
     ([](write-handlers nil))
     ([user-handlers]
      (let [user-handlers (merge {utf8 {"utf8" utf8-writer}} user-handlers)
            handlers (into fressian/clojure-write-handlers
                           (map
                            (fn [[T m]]
                              (let [[k v] (first (seq m))]
                                [T {k (fn->write-handler v)}])))
                           user-handlers)]
        (-> handlers
            fressian/associative-lookup
            fressian/inheritance-lookup)))))

#?(:clj
   (extend-protocol fressian/FressianReadable
     BytesOutputStream
     (to-input-stream [stream]
        (fressian/to-input-stream (ByteBuffer/wrap (.internalBuffer stream) 0 (.length stream))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn create-reader
  "Create a fressian reader targeting in.
   - :handlers is just a map of tag->fn merged with default read handlers
   - :checksum? checks adler validity on each read, throws when fails
   - :name->map-ctor map of record names to map->Record constructors at runtime
       {'string-name' map->some-record}
   - cljs allows reading from :offset"
  [^InputStream in & opts]
  #?(:clj
     (let [in (fressian/to-input-stream in)
           {:keys [handlers checksum?]} (apply hash-map opts)
           handlers (read-handlers handlers)]
       (fressian/create-reader in :handlers handlers :checksum? checksum?))
     :cljs
     (apply r/reader in opts)))

(defn read-object
  "Read a single object from a fressian reader."
  [rdr]
  #?(:clj (fressian/read-object rdr)
     :cljs (r/readObject rdr)))

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
    - :handlers is just a map of {type {'tag' write-fn}} merged with default
      write handlers
    - :record->name (cljs only) map of record ctor to string-name (the string
      version of the record's fully resolved symbol)"
  [^OutputStream out & opts]
  #?(:clj (let [{:keys [handlers] :as opts} (apply hash-map opts)
                handlers (write-handlers handlers)]
            (fressian/create-writer out :handlers handlers))
     :cljs (apply w/writer out opts)))

(defn write-object
  "Write a single object to a fressian writer." ;<= ret?
  ([wrt o]
   #?(:clj (fressian/write-object wrt o)
      :cljs (w/writeObject wrt o)))
  ([wrt o cache?]
   #?(:clj (.writeObject ^FressianWriter wrt o (boolean cache?))
      :cljs (w/writeObject wrt o cache?))))

(defn write-utf8
  "write a string as raw utf-8 bytes"
  ([wrt s](write-utf8 wrt s false))
  ([wrt s cache?] ;cache?
   (assert (string? s))
   #?(:clj
      (write-object wrt (utf8. s) cache?)
      :cljs
      (binding [w/*write-raw-utf8* true
                w/*write-utf8-tag* *write-utf8-tag*]
        (write-object wrt s cache?)))))

(defn write-footer
  [writer]
  #?(:clj (fressian/write-footer writer)
     :cljs (w/writeFooter writer)))

(defn reset-caches
  "write a signal to the reader to forget established cache codes"
  [writer]
  #?(:clj (.resetCaches ^FressianWriter writer)
     :cljs (w/resetCaches writer)))

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
   as a set, e.g. (field-caching-writer #{:color})"
  [cache-pred]
   #?(:clj (fressian/field-caching-writer cache-pred)
      :cljs
      (fn [w rec record->name]
       (w/writeTag w "record" 2)
       (w/writeObject w (w/class-sym rec record->name) true)
       (w/writeTag w "map" 1)
       (w/beginClosedList w)
       (doseq [[field value] rec]
         (w/writeObject w field true)
         (w/writeObject w value (boolean (cache-pred field))))
       (w/endList w))))

(defn byte-stream []
  #?(:clj (BytesOutputStream.)
     :cljs (buf/byte-stream)))

(defn ^ByteBuffer bytestream->buf
  "Return a readable buf over the current internal state of a
   BytesOutputStream."
  [^BytesOutputStream stream]
  #?(:clj
     (ByteBuffer/wrap (.internalBuffer stream) 0 (.length stream))
     :cljs
     (do
       (assert (instance? buf/BytesOutputStream stream))
       (buf/realize stream)))) ;fixed, will not change with more writes! call again

#?(:cljs
   (defn flush-to
     ([stream out](flush-to stream out 0))
     ([stream out offset]
      (assert (instance? buf/BytesOutputStream stream))
      (assert (some? (.-buffer out)))
      (buf/flushTo stream out offset))))

#_(:cljs
   (defn wrap ;TODO
     ([stream out])
     ([stream out offset])))

(defn read
  "Convenience method for reading a single fressian object.
   Takes same options as create-reader"
  [readable & options]
  #?(:clj
     (.readObject ^Reader (apply create-reader (fressian/to-input-stream readable) options))
     :cljs
     (r/readObject (apply create-reader readable options))))

(defn read-batch
  "Read a fressian reader fully (until eof), returning a (possibly empty)
   vector of results."
  [^Reader fin] ; coerce readable to rdr?
  (let [sentinel #?(:clj (Object.) :cljs #js{})]
    (loop [objects (transient [])]
      (let [obj #?(:clj (try (.readObject fin) (catch EOFException e sentinel))
                   :cljs (try (r/readObject fin) (catch js/Error e sentinel)))]
        (if (= obj sentinel)
          (persistent! objects)
          (recur (conj! objects obj)))))))

(defn write
  "Convenience method for writing a single object.  Returns a
   byte buffer.  Options are the same as for create-reader,
   with one additional option :footer? {bool}, if specified will
   write a fressian footer after writing the object."
  [obj & options]
  #?(:clj
     (let [{:keys [footer?]} (apply hash-map options)
           bos (BytesOutputStream.)
           writer ^Writer (apply create-writer bos options)]
       (.writeObject writer obj)
       (when footer?
         (.writeFooter writer))
       (bytestream->buf bos))
     :cljs
     (let [{:keys [footer?]} (when options (apply hash-map options))
           bos (buf/byte-stream)
           writer (apply create-writer bos options)]
       (w/writeObject writer obj)
       (when footer?
         (w/writeFooter writer))
       (buf/close bos))))




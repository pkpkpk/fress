(ns fress.api
  #?(:clj (:refer-clojure :exclude (read)))
  #?(:cljs
     (:require [fress.reader :as r]
               [fress.impl.raw-input :as rawIn]
               [fress.impl.raw-output :as rawOut]
               [fress.writer :as w]
               [fress.impl.buffer :as buf]
               [fress.util :as util])
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

#?(:clj
   (defn ^Reader create-reader
     "Wraps clojure.data.fressian/create-reader but :handlers is just a map of
      tag->fn merged with default read handlers"
     [^InputStream in & {:keys [handlers checksum?]}]
     (let [handlers (read-handlers handlers)]
       (fressian/create-reader in :handlers handlers :checksum? checksum?)))
   :cljs
   (defn create-reader
     "Create a fressian reader targeting in.
      - :handlers must be a map of tag => fn<rdr,tag,field-count>"
     [in & {:keys [handlers validateAdler? offset]
            :or {handlers nil, offset 0, validateAdler? false} :as opts}]
     (when handlers (assert (r/valid-user-handlers? handlers)))
     (let [lookup (r/build-lookup (merge r/default-read-handlers handlers))
           raw-in (rawIn/raw-input in offset validateAdler?)]
       (r/FressianReader. in raw-in lookup nil nil))))

#?(:cljs
   (def ^{:dynamic true
          :doc "map of record names to map->Record constructors at runtime"}
     *record-name->map-ctor* {})) ;; {"string-name" map->some-record}

#?(:clj
   (def read-object fressian/read-object)
   :cljs
   (defn read-object
     "Read a single object from a fressian reader."
     [rdr]
     (binding [r/*record-name->map-ctor* *record-name->map-ctor*]
       (r/readObject rdr))))

#?(:clj
   (def tagged-object? fressian/tagged-object?)
   :cljs
   (defn tagged-object?
     "Returns true if o is a tagged object, which will occur when
      the reader does not recognized a specific type.  Use tag
      and tagged-value to access the contents of a tagged-object."
     [o] (instance? r/TaggedObject o)))

#?(:clj
   (def tag fressian/tag)
   :cljs
   (defn tag
     "Returns the tag if object is a tagged-object, else nil."
     [obj]
     (when (tagged-object? obj)
       (get obj :tag))))

#?(:clj
   (def tagged-value fressian/tagged-value)
   :cljs
   (defn tagged-value
     "Returns the value (an Object arrray) flushToped by obj, or nil
      if obj is not a tagged object."
     [obj]
     (when (tagged-object? obj)
       (get obj :value))))

#?(:clj
   (defn create-writer
     "Wraps clojure.data.fressian/create-writer but :handlers is just a map of
      {type {'tag' write-fn}} merged with default write handlers"
     [^OutputStream out & {:keys [handlers]}]
     (let [handlers (write-handlers handlers)]
       (fressian/create-writer out :handlers handlers)))
   :cljs
   (defn create-writer
     "Create a fressian writer targeting out.
      that combines userHandlers with the normal type handlers
      built into Fressian."
     [out & {:keys [handlers] :as opts}]
     (when handlers (assert (w/valid-user-handlers? handlers)))
     (let [lookup-fn (w/build-handler-lookup handlers)
           raw-out (rawOut/raw-output out)]
       (w/FressianWriter. out raw-out nil nil lookup-fn))))

#?(:cljs
   (def ^{:dynamic true
          :doc "map record-types -> string name desired for serializing records"}
     *record->name* {}))

#?(:clj
   (def write-object fressian/write-object)
   :cljs
   (defn write-object
     "Write a single object to a fressian writer." ;<= ret?
     [wrt o]
     (binding [w/*record->name* *record->name*]
       (w/writeObject wrt o))))

#?(:clj
   (defn write-utf8 [])
   :cljs
   (defn write-utf8
     "write a string as raw utf-8 bytes"
     ([wrt s](write-utf8 wrt s false))
     ([wrt s tag?]
      (assert (string? s))
      (binding [w/*write-raw-utf8* true
                w/*write-utf8-tag* tag?]
        (w/writeString wrt s)))))

#?(:clj
   (def write-footer fressian/write-footer)
   :cljs
   (defn write-footer
     [writer]
     (w/writeFooter writer)))

#?(:clj
   (def begin-closed-list fressian/begin-closed-list)
   :cljs
   (defn ^Writer begin-closed-list
     "Begin writing a fressianed list.  To end the list, call end-list.
      Used to write sequential data whose size is not known in advance."
     [writer]
     (w/beginClosedList writer)))

#?(:clj
   (def end-list fressian/end-list)
   :cljs
   (defn end-list
     "Ends a list begun with begin-closed-list."
     [writer]
     (w/endList writer)))

#?(:clj
   (def begin-open-list fressian/begin-open-list)
   :cljs
   (defn ^Writer begin-open-list
     "Writes fressian code to begin an open list.  An
      open list can be terminated either by a call to end-list,
      or by simply closing the stream.  Used to write sequential
      data whose size is not known in advance, in contexts where
      stream failure can safely be interpreted as end of list."
     [writer]
     (w/beginOpenList writer)))

#?(:clj
   (def field-caching-writer fressian/field-caching-writer)
   :cljs
   (defn field-caching-writer
     "Returns a record writer that caches values for keys
      matching cache-pred, which is typically specified
      as a set, e.g. (field-caching-writer #{:color}).
      CLJS requires binding fress.api/*record->name* to {type 'record-name'}"
     [cache-pred]
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
     ([swrt out](flush-to swrt out 0))
     ([swrt out offset]
      (assert (instance? buf/StreamingWriter swrt))
      (assert (some? (.-buffer out)))
      (buf/flushTo swrt out offset))))

#?(:clj
   (def read fressian/read)
   :cljs
   (defn read
     "Convenience method for reading a single fressian object.
      Takes same options as create-reader.  Readable can be
      any type supported by clojure.java.io/input-stream, or
      a ByteBuffer."
     [readable & options]
     (r/readObject (apply create-reader readable options))))

; #?(:clj
;    (defn read-batch
;      "Read a fressian reader fully (until eof), returning a (possibly empty)
;    vector of results."
;      [^Reader fin]
;      (let [sentinel (Object.)]
;        (loop [objects []]
;          (let [obj (try (.readObject fin) (catch EOFException e sentinel))]
;            (if (= obj sentinel)
;              objects
;              (recur (conj objects obj))))))))

#?(:clj
   (def write fressian/write)
   :cljs
   (defn write
     "Convenience method for writing a single object.  Returns a
      byte buffer.  Options are the same as for create-reader,
      with one additional option.  If footer? is specified, will
      write a fressian footer after writing the object."
     [obj & options]
     (let [{:keys [footer?]} (when options (apply hash-map options))
           bos (buf/streaming-writer)
           writer (apply create-writer bos options)]
       (w/writeObject writer obj)
       (when footer?
         (w/writeFooter writer))
       (buf/close bos))))


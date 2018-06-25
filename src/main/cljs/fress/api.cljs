(ns fress.api
  (:require [fress.reader :as r]
            [fress.impl.raw-input :as rawIn]
            [fress.impl.raw-output :as rawOut]
            [fress.writer :as w]
            [fress.impl.buffer :as buf]
            [fress.util :as util]))

(defn create-reader
  "Create a fressian reader targeting in, which must be compatible
   with clojure.java.io/input-stream.  Handlers must be a map of
   tag => ReadHandler wrapped in associative-lookup. See
   clojure-read-handlers for an example."
  [in & {:keys [handlers validateAdler? offset]
         :or {handlers nil, offset 0, validateAdler? false} :as opts}]
  (when handlers (assert (r/valid-user-handlers? handlers)))
  (let [lookup (r/build-lookup (merge r/default-read-handlers handlers))
        raw-in (rawIn/raw-input in offset validateAdler?)]
    (r/FressianReader. in raw-in lookup nil nil)))

(def ^{:dynamic true
       :doc "map of record names to map->Record constructors at runtime"}
  *record-name->map-ctor* {}) ;; {"string-name" map->some-record}

(defn read-object
  "Read a single object from a fressian reader."
  [rdr]
  (binding [r/*record-name->map-ctor* *record-name->map-ctor*]
    (r/readObject rdr)))

(defn tagged-object?
  "Returns true if o is a tagged object, which will occur when
   the reader does not recognized a specific type.  Use tag
   and tagged-value to access the contents of a tagged-object."
  [o]
  (instance? r/TaggedObject o))

(defn tag
  "Returns the tag if object is a tagged-object, else nil."
  [obj]
  (when (tagged-object? obj)
    (get obj :tag)))

(defn tagged-value
  "Returns the value (an Object arrray) wrapped by obj, or nil
   if obj is not a tagged object."
  [obj]
  (when (tagged-object? obj)
    (get obj :value)))


(defn create-writer
  "Create a fressian writer targeting out.
   that combines userHandlers with the normal type handlers
   built into Fressian."
  [out & {:keys [handlers] :as opts}]
  (when handlers (assert (w/valid-user-handlers? handlers)))
  (let [lookup-fn (w/build-handler-lookup handlers)
        raw-out (rawOut/raw-output out)]
    (w/FressianWriter. out raw-out nil nil lookup-fn)))

(def ^{:dynamic true
       :doc "map record-types -> string name desired for serializing records"}
  *record->name* {})

(defn write-object
  "Write a single object to a fressian writer." ;<= ret?
  [wrt o]
  (binding [w/*record->name* *record->name*]
    (w/writeObject wrt o)))

(defn write-utf8
  "write a string as raw utf-8 bytes"
  ([wrt s](write-utf8 wrt s false))
  ([wrt s tag?]
   (assert (string? s))
   (binding [w/*write-raw-utf8* true
             w/*write-utf8-tag* tag?]
     (w/writeString wrt s))))

(defn write-footer
  [writer]
  (w/writeFooter writer))

(defn ^Writer begin-closed-list
  "Begin writing a fressianed list.  To end the list, call end-list.
   Used to write sequential data whose size is not known in advance."
  [^StreamingWriter writer]
  (w/beginClosedList writer))

(defn ^Writer end-list
  "Ends a list begun with begin-closed-list."
  [^StreamingWriter writer]
  (w/endList writer))

(defn ^Writer begin-open-list
  "Writes fressian code to begin an open list.  An
   open list can be terminated either by a call to end-list,
   or by simply closing the stream.  Used to write sequential
   data whose size is not known in advance, in contexts where
   stream failure can safely be interpreted as end of list."
  [^StreamingWriter writer]
  (w/beginOpenList writer))

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
      (reduce-kv
       (fn [^Writer w k v]
         (w/writeObject w k true)
         (w/writeObject w v (boolean (cache-pred k))))
       w
       rec)
      (w/endList w))))

(defn streaming-writer []
  (let [bytesWritten 0
        open? true
        buffer nil]
    (buf/StreamingWriter. #js[] bytesWritten open? buffer)))

(defn flush-to
  ([swrt out](flush-to swrt out 0))
  ([swrt out offset]
   (assert (instance? buf/StreamingWriter swrt))
   (assert (some? (.-buffer out)))
   (buf/wrap swrt out offset)))

(defn- ^ByteBuffer bytestream->buf
  "Return a readable buf over the current internal state of a
   BytesOutputStream."
  [^BytesOutputStream stream]
  (buf/realize stream)) ;does not close!

; (defn read
;   "Convenience method for reading a single fressian object.
;    Takes same options as create-reader.  Readable can be
;    any type supported by clojure.java.io/input-stream, or
;    a ByteBuffer."
;   [readable & options]
;   (.readObject ^Reader (apply create-reader (to-input-stream readable) options)))
;
; (defn ^ByteBuffer write
;   "Convenience method for writing a single object.  Returns a
;    byte buffer.  Options are the same as for create-reader,
;    with one additional option.  If footer? is specified, will
;    write a fressian footer after writing the object."
;   ([obj & options]
;      (let [{:keys [footer?]} (when options (apply hash-map options))
;            bos (BytesOutputStream.)
;            writer ^Writer (apply create-writer bos options)]
;        (.writeObject writer obj)
;        (when footer?
;          (.writeFooter writer))
;        (bytestream->buf bos))))
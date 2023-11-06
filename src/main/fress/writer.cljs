(ns fress.writer
  (:require-macros [fress.macros :refer [>>>]])
  (:require [clojure.string :as string]
            [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.hopmap :as hop]
            [fress.impl.table :as table]
            [fress.impl.bigint :as bn :refer [bigint]]
            [fress.util :as util]
            [goog.object]))

(def ^:dynamic *write-raw-utf8* false)
(def ^:dynamic *write-utf8-tag* false)
(def ^:dynamic *stringify-keys* false)

(defprotocol IFressianWriter
  (writeNull ^FressianWriter [this])
  (writeBoolean ^FressianWriter [this b])
  (writeInt ^FressianWriter [this i])
  (writeDouble ^FressianWriter [this d])
  (writeFloat ^FressianWriter  [this f])
  (writeStringNoChunk- [this ^string s])
  (writeString- ^FressianWriter [this s])
  (writeString ^FressianWriter [this s])
  (writeList ^FressianWriter [this o])
  (writeBytes ^FressianWriter [this bs] [this bs offset length])
  (writeFooter ^FressianWriter [this])
  (clearCaches [this])
  (resetCaches ^FressianWriter [this])
  (getPriorityCache [this])
  (getStructCache [this])
  (writeTag ^FressianWriter [this tag componentCount])
  (writeCount [this n])
  (shouldSkipCache- ^boolean [this o])
  (doWrite- [this tag o w cache?])
  (writeAs ^FressianWriter [this tag o] [this tag o cache?])
  (writeObject ^FressianWriter [this o] [this o cache?])
  (writeCode [this code])
  (beginOpenList ^FressianWriter [this])
  (beginClosedList ^FressianWriter [this])
  (endList ^FressianWriter [this]))

(defn ^number bit-switch
  "@return {number} bits not needed to represent this number"
  [l]
  (- 64 (.-length (.toString (Math/abs l) 2))))

(defn internalWriteInt [wtr ^number n]
  (let [s (bit-switch n)
        raw (.-raw-out wtr)]
    (cond
      (<= 58 s 64)
      (do
        (when (< n -1)
          (rawOut/writeRawByte raw (+ codes/INT_PACKED_2_ZERO (>>> n 8))))
        (rawOut/writeRawByte raw n))

      (<= 52 s 57)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_2_ZERO (>>> n 8)))
        (rawOut/writeRawByte raw n))

      (<= 45 s 51)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_3_ZERO (>>> n 16)))
        (rawOut/writeRawInt16 raw n))

      (<= 39 s 44)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_4_ZERO (>>> n 24)))
        (rawOut/writeRawInt24 raw n))

      (<= 31 s 38)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_5_ZERO (>>> n 32)))
        (rawOut/writeRawInt32 raw n))

      (<= 23 s 30)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_6_ZERO (>>> n 40)))
        (rawOut/writeRawInt40 raw n))

      (<= 15 s 22)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_7_ZERO (>>> n 48)))
        (rawOut/writeRawInt48 raw n))

      (js/Number.isSafeInteger n)
      (do
        (writeCode wtr codes/INT)
        (rawOut/writeRawInt64 raw n))

      true
      (throw (js/Error. (str "cannot write unsafe integer: " (pr-str n)))))))

(defn internalWriteFooter [wrt ^number length]
  (let [raw-out (.-raw-out wrt)]
    (rawOut/writeRawInt32 raw-out codes/FOOTER_MAGIC)
    (rawOut/writeRawInt32 raw-out length)
    (rawOut/writeRawInt32 raw-out (rawOut/getChecksum raw-out))))

(defn writeRawUTF8
  "We can use native TextEncoder to remove some dirty work, also chunking is
   pointless for WASM."
  [this ^string s]
  (assert (string? s))
  (let [bytes (.encode util/TextEncoder s)
        length (.-byteLength bytes)]
    ; need to test if jvm can still read this
    (if *write-utf8-tag*
      ;; needs to be picked up server-side by a registered "utf8" reader
      ;; we do this because we cant modify codes serverside but still prefer them
      ;; client side because its a simple int dispatch instead of reading the tag
      (writeTag this "utf8" 2); writeCount + rawbytes
      (writeCode this codes/UTF8))
    (writeCount this length)
    (rawOut/writeRawBytes (.-raw-out this) bytes 0 length))
  this)

(defn buffer-string-chunk-utf8
  "starting with position start in s, write as much of s as possible into byteBuffer
   using UTF-8.
   returns {stringpos, bufpos}"
  [s start buf]
  (loop [string-pos start
         buffer-pos 0]
    (if-not (< string-pos (alength s))
      [string-pos buffer-pos]
      (let [ch (.charCodeAt s string-pos)
            ;; "src/org/fressian/impl/Fns.java:117:4"
            encoding-size (if (<= ch 0x007f) 1 (if (< 0x07ff ch) 3 2))]
        (if (< (alength buf) (+ buffer-pos encoding-size))
          [string-pos buffer-pos]
          (do
            (case encoding-size
              1 (aset buf buffer-pos ch)
              2 (do
                  (aset buf buffer-pos       (bit-or 0xc0 (bit-and (>>> ch 6) 0x1f)))
                  (aset buf (inc buffer-pos) (bit-or 0x80 (bit-and (>>> ch 0) 0x3f))))
              3 (do
                  (aset buf buffer-pos       (bit-or 0xe0 (bit-and (>>> ch 12) 0x0f)))
                  (aset buf (inc buffer-pos) (bit-or 0x80 (bit-and (>>> ch 6)  0x3f)))
                  (aset buf (+ buffer-pos 2) (bit-or 0x80 (bit-and (>>> ch 0)  0x3f)))))
            (recur (inc string-pos) (+ buffer-pos encoding-size))))))))

(defn defaultWriteString [this s]
  (let [max-buf-needed (min (* (count s) 3) 65536)
        string-buffer (js/Int8Array. (js/ArrayBuffer. max-buf-needed))]
    (loop [[string-pos buf-pos] (buffer-string-chunk-utf8 s 0 string-buffer)]
      (if (< buf-pos ranges/STRING_PACKED_LENGTH_END)
        (writeCode this (+ codes/STRING_PACKED_LENGTH_START buf-pos))
        (if (= string-pos (count s))
          (do
            (writeCode this codes/STRING)
            (writeCount this buf-pos))
          (do
            (writeCode this codes/STRING_CHUNK)
            (writeInt this buf-pos))))
      (rawOut/writeRawBytes (.-raw-out this) string-buffer 0 buf-pos)
      (when (< string-pos (count s))
        (recur (buffer-string-chunk-utf8 s string-pos string-buffer))))))

(deftype FressianWriter [out raw-out priorityCache structCache ^fn lookup]
  IFressianWriter
  (writeCode [this code] (rawOut/writeRawByte raw-out code))

  (writeCount [this n] (writeInt this n))

  (writeNull [this] (writeCode this codes/NULL))

  (writeBoolean [this b]
    (if (nil? b)
      (writeNull this)
      (let [b (boolean b)]
        (if (true? b)
          (writeCode this codes/TRUE)
          (writeCode this codes/FALSE))))
    this)

  (writeInt [this ^number i]
    (if (nil? i)
      (writeNull this)
      (do
        (assert (integer? i))
        (internalWriteInt this i)))
    this)

  (writeFloat [this ^number f]
    (do
      (writeCode this codes/FLOAT)
      (rawOut/writeRawFloat raw-out f)
      this))

  (writeDouble [this ^number d]
    (if (== d 0.0)
      (writeCode this codes/DOUBLE_0)
      (if (== d 1.0)
        (writeCode this codes/DOUBLE_1)
        (do
          (writeCode this codes/DOUBLE)
          (rawOut/writeRawDouble raw-out d))))
    this)

  (writeBytes [this bytes]
    (if (nil? bytes)
      (writeNull this)
      (writeBytes this bytes 0 (.-byteLength bytes)))
    this)

  (writeBytes [this bytes offset length]
    (assert (or (instance? js/Int8Array bytes) (instance? js/Uint8Array bytes)) "writeRawBytes expects a Int8 Array")
    (if (< length ranges/BYTES_PACKED_LENGTH_END)
      (do
        (rawOut/writeRawByte raw-out (+ codes/BYTES_PACKED_LENGTH_START length))
        (rawOut/writeRawBytes raw-out bytes offset length))
      (loop [len length
             off offset]
        (if (< ranges/BYTE_CHUNK_SIZE len)
          (do
            (writeCode this codes/BYTES_CHUNK)
            (writeCount this ranges/BYTE_CHUNK_SIZE)
            (rawOut/writeRawBytes raw-out bytes off ranges/BYTE_CHUNK_SIZE)
            (recur
              (- len ranges/BYTE_CHUNK_SIZE)
              (+ off ranges/BYTE_CHUNK_SIZE)))
          (do
            (writeCode this codes/BYTES)
            (writeCount this len)
            (rawOut/writeRawBytes raw-out bytes off len)))))
    this)

  (writeString [this s]
    (if ^boolean *write-raw-utf8*
      (writeRawUTF8 this s)
      (defaultWriteString this s)))

  (writeObject [this o] (writeAs this nil o))
  (writeObject [this o cache?] (writeAs this nil o cache?))

  (writeAs [this tag o] (writeAs this tag o false))
  (writeAs [this tag o cache?]
    (if-let [handler (lookup tag o)]
      (doWrite- this tag o handler cache?)
      (throw (js/Error. (str "no handler for tag :" (pr-str tag) ", type: " (pr-str (type o)))))))

  (getPriorityCache [this]
    (or priorityCache (let [c (hop/hopmap 16)] (set! (.-priorityCache this) c) c)))

  (getStructCache [this]
    (or structCache (let [c (hop/hopmap 16)] (set! (.-structCache this) c) c)))

  (clearCaches [this]
    (when (and priorityCache (not (hop/isEmpty priorityCache)))
      (hop/clear priorityCache))
    (when (and structCache (not (hop/isEmpty structCache)))
      (hop/clear structCache)))

  (resetCaches [this]
    (writeCode this codes/RESET_CACHES)
    (clearCaches this)
    this)

  (shouldSkipCache- ^boolean [this o]
    (or
     (nil? o)
     (boolean? o)
     ^boolean
     (and (string? o)
          (zero? (.-length o)))
     ^boolean
     (and (number? o)
          (or (== 0.0 o) (== 1.0 o)))))

  (doWrite- [this tag o handler ^boolean cache?]
    (if ^boolean (or (not cache?) (shouldSkipCache- this o))
      (handler this o)
      (let [index (hop/oldIndex (getPriorityCache this) o)]
        (if (== index -1)
          (do ;;newly interned, write PUT + object
            (writeCode this codes/PUT_PRIORITY_CACHE)
            (doWrite- this tag o handler false))
          ;;already cached
          (if (< index ranges/PRIORITY_CACHE_PACKED_END)
            (writeCode this (+ codes/PRIORITY_CACHE_PACKED_START index))
            (do ;;past cache packing
              (writeCode this codes/GET_PRIORITY_CACHE)
              (writeInt this index)))))))

  (writeList [this lst]
    (if (nil? lst)
      (writeNull this)
      (let [length (count lst)]
        (if (< length ranges/LIST_PACKED_LENGTH_END)
          (rawOut/writeRawByte raw-out (+ length codes/LIST_PACKED_LENGTH_START))
          (do
            (writeCode this codes/LIST)
            (writeCount this length)))
        (doseq [item lst]
          (writeObject this item))))
    this)

  (beginOpenList [this]
    (if-not (zero? (rawOut/getBytesWritten raw-out))
      (throw
        (js/Error. "openList must be called from the top level, outside any footer context."))
      (writeCode this codes/BEGIN_OPEN_LIST))
    this)

  (beginClosedList [this]
    (writeCode this codes/BEGIN_CLOSED_LIST)
    this)

  (endList [this]
    (writeCode this codes/END_COLLECTION)
    this)

  (writeTag [this tag ^number component-count]
    (if-let [shortcut-code (goog.object/get codes/tag->code tag)]
      (writeCode this shortcut-code)
      (let [index (hop/oldIndex (getStructCache this) tag)]
        (cond
          (== index -1)
          (do
            (assert (string? tag) "tag needs to be a string")
            (writeCode this codes/STRUCTTYPE)
            ;; cannot control how keys are written on JVM so leaving as default
            ; (writeString this tag)
            (defaultWriteString this tag)
            (writeInt this component-count))

          (< index ranges/STRUCT_CACHE_PACKED_END)
          (writeCode this (+ codes/STRUCT_CACHE_PACKED_START index))

          true
          (do
            (writeCode this codes/STRUCT) ;<= when cache length exceeds packing
            (writeInt this index)))))
    this)

  (writeFooter [this]
    (internalWriteFooter this (rawOut/getBytesWritten raw-out))
    (clearCaches this)
    this))

(defn writeNumber [this ^number n]
  (if (integer? n)
    (writeInt this n)
    (if (<= util/f32_MIN_VALUE n util/f32_MAX_VALUE)
      (writeFloat this n)
      (writeDouble this n)))
  this)

(defn fullname [kw]
  (if-not (qualified-keyword? kw)
    (name kw)
    (let [_ns (namespace kw)
          _name (name kw)]
      (str _ns "/" _name))))

(defn writeMap [wrt m]
  (writeTag wrt "map" 1)
  (if-not ^boolean *stringify-keys*
    (writeList wrt (mapcat identity (seq m)))
    (writeList wrt (mapcat (fn [[k v :as entry]]
                             (if (keyword? k)
                               [(fullname k) v]
                               entry))
                           (seq m)))))

(defn- writeNamed [tag wtr s]
  (writeTag wtr tag 2)
  (writeObject wtr (namespace s) true)
  (writeObject wtr (name s) true))

(defn writeSet [wtr s]
  (writeTag wtr "set" 1)
  (writeList wtr (into [] s)))

(defn writeInst [wtr date]
  (writeTag wtr "inst" 1)
  (writeInt wtr (.getTime date)))

(defn writeUri [wtr u]
  (writeTag wtr "uri" 1)
  (writeString wtr (.toString u)))

(defn writeRegex [wtr re]
  (writeTag wtr "regex" 1)
  (writeString wtr (.-source re)))

(defn writeUUID [wtr u]
  ; adapted from https://github.com/kawasima/fressian-cljs/blob/master/src/cljs/fressian_cljs/uuid.cljs
  (writeTag wtr "uuid" 1)
  (let [buf (make-array 16)
        idx (atom 0)]
    (string/replace (.toLowerCase (.-uuid u)) #"[0-9a-f]{2}"
      (fn [oct]
        (when (< @idx 16)
          (aset buf @idx (js/parseInt (str "0x" oct)))
          (swap! idx inc))))
    (writeBytes wtr (js/Uint8Array. buf))))

(defn writeByteArray [wrt bytes]
  (writeBytes wrt bytes))

(defn writeIntArray [wtr a]
  (writeTag wtr "int[]" 2)
  (let [length (alength a)]
    (writeInt wtr length)
    (loop [i 0]
      (when (< i length)
        (writeInt wtr (aget a i))
        (recur (inc i))))))

(defn writeFloatArray [wtr a]
  (writeTag wtr "float[]" 2)
  (let [length (alength a)]
    (writeInt wtr length)
    (loop [i 0]
      (when (< i length)
        (writeFloat wtr (aget a i))
        (recur (inc i))))))

(defn writeDoubleArray [wtr a]
  (writeTag wtr "double[]" 2)
  (let [length (alength a)]
    (writeInt wtr length)
    (loop [i 0]
      (when (< i length)
        (writeDouble wtr (aget a i))
        (recur (inc i))))))

(defn writeBooleanArray [wtr a]
  (let [length (count a)]
    (writeTag wtr "boolean[]" 2)
    (writeInt wtr length)
    (doseq [b a]
      (writeBoolean wtr b))))

(defn writeLongArray [wtr arr]
  (let [length (alength arr)]
    (writeTag wtr "long[]" 2)
    (writeInt wtr length)
    (doseq [l arr] (writeInt wtr l))))

(defn writeObjectArray [wtr arr]
  (let [length (alength arr)]
    (writeTag wtr "Object[]" 2)
    (writeInt wtr length)
    (doseq [o arr] (writeObject wtr o))))

(defn class-sym
  "Record types need a string so the name can survive munging. Is converted to
   symbol before serializing."
  [rec rec->tag]
  (let [name (get rec->tag (type rec))]
    (if (string? name)
      (symbol name)
      (throw (js/Error. "writing records requires corresponding entry in *record->name*")))))

(defn writeRecord [w rec rec->tag]
  (writeTag w "record" 2)
  (writeObject w (class-sym rec rec->tag) true)
  (writeTag w "map" 1)
  (beginClosedList w)
  (doseq [[field value] rec]
    (writeObject w field true)
    (writeObject w value))
  (endList w))

(defn write-bigint
  [^FressianWriter wrt n]
  (writeTag wrt "bigint" 1)
  (writeBytes wrt (bn/bigint->bytes n)))

(defn writeBigInt64
  "written as normal ints where unsafe numbers would be read back as longs"
  [w ^js/BigInt n]
  (assert (bn/bigint? n))
  (if (<= js/Number.MIN_SAFE_INTEGER n js/Number.MAX_SAFE_INTEGER)
    (internalWriteInt w (js/Number n))
    (let [s (bn/bit-switch n)
          raw (.-raw-out w)]
      (writeCode w codes/INT)
      (rawOut/writeRawByte raw (js/Number (bit-and (bn/>> n (js* "56n")) (js* "0xFFn"))))
      (rawOut/writeRawByte raw (js/Number (bit-and (bn/>> n (js* "48n")) (js* "0xFFn"))))
      (rawOut/writeRawByte raw (js/Number (bit-and (bn/>> n (js* "40n")) (js* "0xFFn"))))
      (rawOut/writeRawByte raw (js/Number (bit-and (bn/>> n (js* "32n")) (js* "0xFFn"))))
      (rawOut/writeRawByte raw (js/Number (bit-and (bn/>> n (js* "24n")) (js* "0xFFn"))))
      (rawOut/writeRawByte raw (js/Number (bit-and (bn/>> n (js* "16n")) (js* "0xFFn"))))
      (rawOut/writeRawByte raw (js/Number (bit-and (bn/>> n (js*  "8n")) (js* "0xFFn"))))
      (rawOut/writeRawByte raw (js/Number (bit-and n (js* "0xFFn")))))))

(defn writeBigInt64Array [w arr]
  (assert (instance? js/BigInt64Array arr))
  (writeTag w "long[]" 2)
  (writeInt w (alength arr))
  (doseq [bn arr]
    (writeBigInt64 w bn)))

(defn writeChar
  [^FressianWriter wrt s]
  (assert (string? s))
  (assert (== 1 (alength s)))
  (writeTag wrt "char" 1)
  (writeInt wrt (.charCodeAt s 0)))

(def ^{:doc "@suppress {checkRegExp}"}
  default-write-handlers
  (table/from-array #js[
    js/Number writeNumber
    js/String writeString
    js/Boolean writeBoolean
    js/Array writeList
    js/Date writeInst
    js/RegExp writeRegex
    js/Int8Array writeByteArray
    js/Uint8Array writeByteArray
    js/Int32Array writeIntArray
    js/Float32Array writeFloatArray
    js/Float64Array writeDoubleArray
    js/BigInt64Array writeBigInt64Array
    js/BigInt write-bigint
    goog.Uri writeUri
    nil writeNull
    cljs.core/UUID writeUUID
    cljs.core/PersistentHashMap writeMap
    cljs.core/PersistentArrayMap writeMap
    cljs.core/ObjMap writeMap
    cljs.core/MapEntry writeList
    cljs.core/PersistentVector writeList
    cljs.core/EmptyList writeList
    cljs.core/List writeList
    cljs.core/ChunkedSeq writeList
    cljs.core/PersistentHashSet writeSet
    cljs.core/Keyword #(writeNamed "key" %1 %2)
    cljs.core/Symbol #(writeNamed "sym" %1 %2)
    "boolean[]" writeBooleanArray
    "long[]" writeLongArray
    "Object[]" writeObjectArray
    "char" writeChar]))

(defn build-inheritance-lookup [handlers]
  (let [fns (filter fn? (.keys handlers))]
    (fn [o]
      (loop [fns fns]
        (when (seq fns)
          (let [f (first fns)]
            (if (instance? f o)
              (.?get handlers f)
              (recur (rest fns)))))))))

(defn build-handler-lookup
  [user-handlers rec->tag]
  (let [handlers (if (empty? user-handlers) ; TODO table check to allow user supplied subset
                   default-write-handlers
                   (.add-handlers (table/from-table default-write-handlers) user-handlers))
        inh-lookup (build-inheritance-lookup handlers)] ;;TODO delay / build this lazily/if needed,
    (fn [tag obj]
      (if (some? tag)
        (.?get handlers tag)
        (if (record? obj)
          (or (.?get handlers (type obj))
              (if-let [custom-writer (.?get handlers "record")]
                (fn [wrt rec]
                  (custom-writer wrt rec rec->tag))
                (fn [wrt rec]
                  (writeRecord wrt rec rec->tag))))
          (if (object? obj)
            (fn [wrt obj]
              (when goog.DEBUG
                (js/console.warn "js->clj used to write javascript object into fressian!"))
              (writeMap wrt (js->clj obj)))
            (or (.?get handlers (type obj))
                (inh-lookup obj))))))))

(defn ^boolean valid-handler-key?
  "singular or coll of constructors and string tags"
  [k]
  (if (coll? k)
    (every? #(or (fn? %) (string? %)) k)
    (or (fn? k) (string? k))))

(defn valid-user-handlers?
  [uh]
  (and (map? uh)
       (every? fn? (vals uh))
       (every? valid-handler-key? (keys uh))))

(defn valid-record->name?
  "each key should be record ctor"
  [m]
  (and (map? m)
       (every? fn? (keys m))
       (every? string? (vals m))))

(defn normalize-handlers
  "Normalize type->tag->writer (a la data.fressian) to flat type->writer"
  [user-handlers]
  (reduce-kv (fn [acc type tag-writer-map]
               (if (map? tag-writer-map)
                 (assoc acc type (val (first tag-writer-map)))
                 (assoc acc type tag-writer-map)))
             {}
             user-handlers))

(defn writer
  "Create a writer that combines userHandlers with the normal type handlers
   built into Fressian."
  [out & {:keys [handlers record->name checksum? offset] :as opts}]
  (let [handlers (some-> handlers normalize-handlers)]
    (when handlers (assert (valid-user-handlers? handlers) "invalid write handler shape"))
    (when record->name (assert (valid-record->name? record->name)))
    (let [lookup-fn (build-handler-lookup handlers record->name)
          checksum? (if (some? checksum?) checksum? true)
          raw-out (rawOut/raw-output out {:offset (or offset 0) :checksum? checksum?})
          priorityCache nil ;added when needed
          structCache nil]
      (FressianWriter. out raw-out priorityCache structCache lookup-fn))))

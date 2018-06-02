(ns fress.writer
  (:require-macros [fress.macros :refer [>>>]])
  (:require [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.uuid :as uuid]
            [fress.impl.hopmap :as hop]
            [fress.util :as util :refer [log dbg]]))

(defn utf8-encoding-size
  "src/org/fressian/impl/Fns.java:117:4"
  [ch]
  (assert (int? ch) "ch should be charCode taken from string index")
  (if (<= ch 0x007f)
    1
    (if (< 0x07ff ch)
      3
      2)))

(defn buffer-string-chunk-utf8
  "starting with position start in s, write as much of s as possible into byteBuffer
   using UTF-8.
   returns {stringpos, bufpos}"
  [s start buf]
  (loop [string-pos start
         buffer-pos 0]
    (if (< string-pos (alength s))
      (let [ ch (.charCodeAt s string-pos)
            encoding-size (utf8-encoding-size ch)]
        (if (< (alength buf) (+ buffer-pos encoding-size))
          [string-pos buffer-pos]
          (do
            (case encoding-size
              1 (aset buf buffer-pos ch)
              2 (do
                  (aset buf buffer-pos       (bit-or 0xc0 (bit-and (bit-shift-right ch 6) 0x1f)))
                  (aset buf (inc buffer-pos) (bit-or 0x80 (bit-and (bit-shift-right ch 0) 0x3f))))
              3 (do
                  (aset buf buffer-pos       (bit-or 0xe0 (bit-and (bit-shift-right ch 12) 0x0f)))
                  (aset buf (inc buffer-pos) (bit-or 0x80 (bit-and (bit-shift-right ch 6)  0x3f)))
                  (aset buf (+ buffer-pos 2) (bit-or 0x80 (bit-and (bit-shift-right ch 0)  0x3f)))))
            (recur (inc string-pos) (+ buffer-pos encoding-size)))))
      [string-pos buffer-pos])))

(defprotocol IFressianWriter
  (writeNull ^FressianWriter [this])
  (writeBoolean ^FressianWriter [this b])
  (writeInt ^FressianWriter [this i])
  (writeDouble ^FressianWriter [this d])
  (writeFloat ^FressianWriter  [this f])
  (writeStringNoChunk- [this ^string s])
  (writeString- ^FressianWriter [this s])
  (writeString ^FressianWriter [this s])
  ; (writeIterator [this length it])
  (writeList ^FressianWriter [this o])
  (writeBytes ^FressianWriter [this bs] [this bs offset length])
  ; (writeFooterFor [this byteBuffer])
  (writeFooter ^FressianWriter [this])
  (close [this] "public")

  (clearCaches [this])
  (resetCaches ^FressianWriter [this]"public")

  (getPriorityCache ^InterleavedIndexHopMap [this]"public")
  (getStructCache ^InterleavedIndexHopMap [this]"public")
  (writeTag ^FressianWriter [this tag componentCount] "public")
  ; (writeExt ^FressianWriter [this]"public")
  (writeCount [this n] "public")
  (shouldSkipCache- ^boolean [this o] "private")
  (doWrite- [this tag o w cache?] "private")
  (writeAs ^FressianWriter [this tag o] [this tag o cache?] "public")
  (writeObject ^FressianWriter [this o] [this o cache?] "public")
  (writeCode [this code] "public")
  (beginOpenList ^FressianWriter [this] "public")
  (beginClosedList ^FressianWriter [this] "public")
  (endList ^FressianWriter [this] "public")
  (getByte [this index]))

(defn ^number bit-switch
  "@return {number}(bits not needed to represent this number) + 1"
  [l]
  (- 64 (.-length (.toString (.abs js/Math l) 2))))

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

      :else
      (do
        (writeCode wtr codes/INT)
        (rawOut/writeRawInt64 raw n)))))

(defn internalWriteFooter [wrt ^number length]
  (let [raw-out (.-raw-out wrt)]
    (rawOut/writeRawInt32 raw-out codes/FOOTER_MAGIC)
    (rawOut/writeRawInt32 raw-out length)
    (rawOut/writeRawInt32 raw-out (rawOut/getChecksum raw-out))
    (rawOut/reset raw-out)))

(def ^:dynamic *write-raw-utf8* false)
(def ^:dynamic *write-utf8-tag* false)

(defn writeRawUTF8
  "We can use native TextEncoder to remove some dirty work, also chunking is
   pointless for WASM."
  [this ^string s]
  (assert (string? s))
  (let [bytes (.encode util/TextEncoder s)
        length (.-byteLength bytes)]
    ; may need unique code here, breaking std fressian behavior
    ; need to test if jvm can still read this
    (if *write-utf8-tag*
      ;; needs to be picked up server-side by a registered "utf8" reader
      ;; we do this because we cant modify codes serverside but still prefer them
      ;; client side because its a simple int dispatch instead of reading the tag
      (writeTag this "utf8" 2); writeCount + rawbytes ?
      (writeCode this codes/UTF8))
    (writeCount this length)
    (rawOut/writeRawBytes (.-raw-out this) bytes 0 length))
  this)

(defn defaultWriteString [this s]
  (let [max-buf-needed (min (* (count s) 3) 65536) ;;
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
  (getByte [this index] (rawOut/getByte raw-out index))

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
        (assert (int? i))
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
    (cond
      (or (nil? o) (= (type o) js/Boolean)) true
      (= (type o) js/String) (= (count o) 0)
      (number? o) (or (== 0.0 o) (== 1.0 o))
      :default false))

  (doWrite- [this tag o handler cache?]
    (if ^boolean cache?
      (if ^boolean (shouldSkipCache- this o)
        (doWrite this tag o handler false)
        (let [index (hop/oldIndex (getPriorityCache this) o)]
          (if (= index -1)
            (do
              (writeCode this codes/PUT_PRIORITY_CACHE)
              (doWrite this tag o handler false))
            (if (< index ranges/PRIORITY_CACHE_PACKED_END)
              (writeCode this (+ codes/PRIORITY_CACHE_PACKED_START index))
              (do
                (writeCode this codes/GET_PRIORITY_CACHE)
                (writeInt this index))))))
      (handler this o)))

  (writeList [this lst]
    (if (nil? lst)
      (writeNull this)
      (let [length (count lst)]
        (if (< length ranges/LIST_PACKED_LENGTH_END)
          ;;;packed means when just skip count, rely on reader to read up to packed-length
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
    (if-let [shortcut-code (codes/tag->code tag)]
      (writeCode this shortcut-code)
      (let [index (hop/oldIndex (getStructCache this) tag)]
        (cond
          (== index -1)
          (do
            (assert (string? tag) "tag needs to be a string")
            (writeCode this codes/STRUCTTYPE)
            (defaultWriteString this tag)
            (writeInt this component-count))

          (< index ranges/STRUCT_CACHE_PACKED_END)
          (writeCode this (+ codes/STRUCT_CACHE_PACKED_START index))

          :default
          (do
            (writeCode this codes/STRUCT) ;<= when cache length exceeds packing
            (writeInt this index)))))
    this)

  ; (writeFooterFor [this bytes])
  ; (writeExt [this ...])

  (writeFooter [this]
    (internalWriteFooter this (rawOut/getBytesWritten raw-out))
    (clearCaches this)
    this))

(defn writeNumber [this ^number n]
  (if (int? n)
    (writeInt this n)
    (if (<= util/F32_MIN_VALUE n util/F32_MAX_VALUE)
      (writeFloat this n)
      (writeDouble this n)))
  this)

(defn writeMap [wrt m]
  (writeTag wrt "map" 1)
  (writeList wrt (mapcat identity (seq m))))

(defn- writeNamed [tag wtr s]
  (writeTag wtr tag 2)
  ; (writeObject wtr (namespace s) true)
  (writeObject wtr (namespace s))
  ; (writeObject wtr (name s) true)
  (writeObject wtr (name s))
  )

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
  (writeTag wtr "uuid" 1)
  (writeBytes wtr (uuid/uuid->bytes u)))

(defn writeByteArray [wrt bytes]
  ; (writeTag wtr "uuid" 1)
  (writeBytes wrt bytes)
  )

(defn writeIntArray [wtr a]
  (writeTag wtr "int[]" 1)
  (let [length (alength a)]
    (writeInt wtr length)
    (loop [i 0]
      (when (< i length)
        (writeInt wtr (aget a i))
        (recur (inc i))))))

(defn writeFloatArray [wtr a]
  (writeTag wtr "float[]" 1)
  (let [length (alength a)]
    (writeInt wtr length)
    (loop [i 0]
      (when (< i length)
        (writeFloat wtr (aget a i))
        (recur (inc i))))))

(defn writeDoubleArray [wtr a]
  (writeTag wtr "double[]" 1)
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



#_(defn switch-long [l] (- 64 (.getNumBitsAbs l)))

#_(defn writeRawInt64 [this l]
  (dotimes [x 8]
     (writeRawByte this (.shiftRight l (* (- 7 x) 8)))))

#_(defn writeLong [wtr l]
  (let [s (switch-long l)]
    wtr))

#_(defn writeLongArray [wtr a]
  (let [length (count a)]
    (writeTag wtr "long[]" 2)
    (writeInt wtr length)
    (doseq [l a]
      (writeLong wtr l))))

; goog.math.Long writeLong
; "long[]" writeLongArray
; object[]

(def default-write-handlers
  {js/Number writeNumber
   js/String writeString
   js/Boolean writeBoolean
   js/Object #(writeMap %1 (js->clj %2))
   js/Array writeList
   js/Date writeInst
   js/RegExp writeRegex
   js/Int8Array writeByteArray
   js/Int32Array writeIntArray
   js/Float32Array writeFloatArray
   js/Float64Array writeDoubleArray
   goog.Uri writeUri
   nil writeNull
   cljs.core/UUID writeUUID
   cljs.core/PersistentHashMap writeMap
   cljs.core/PersistentArrayMap writeMap
   cljs.core/ObjMap writeMap
   cljs.core/PersistentVector writeList
   cljs.core/ChunkedSeq writeList
   cljs.core/PersistentHashSet writeSet
   cljs.core/Keyword #(writeNamed "key" %1 %2)
   cljs.core/Symbol #(writeNamed "sym" %1 %2)
   "boolean[]" writeBooleanArray})

(defn build-handler-lookup
  [user-handlers]
  (let [handlers (merge default-write-handlers user-handlers)]
    (fn [tag obj]
      (if tag
        (get handlers tag)
        (get handlers (type obj))))))

(defn writer
  "Create a writer that combines userHandlers with the normal type handlers
   built into Fressian."
  [out & {:keys [handlers]}]
  (let [lookup-fn (build-handler-lookup handlers)
        raw-out (rawOut/raw-output out)
        priorityCache (hop/hopmap 16)
        structCache (hop/hopmap 16)]
    (FressianWriter. out raw-out priorityCache structCache lookup-fn)))
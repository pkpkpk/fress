(ns fress.writer
  (:require-macros [fress.macros :refer [>>>]])
  (:require [fress.codes :as codes]
            [fress.ranges :as ranges]
            [fress.raw-output :as rawOut]
            [goog.string :as gstring]))

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
  (writeString ^FressianWriter [this s])
  (writeIterator [this length it])
  (writeList ^FressianWriter [this o])
  (writeBytes ^FressianWriter
              [this bs]
              [this bs offset length])
  (writeFooterFor [this byteBuffer])
  (writeFooter ^FressianWriter [this])
  (internalWriteFooter [this length])
  (clearCaches [this])
  (resetCaches ^FressianWriter [this]"public")
  (getPriorityCache ^InterleavedIndexHopMap [this]"public")
  (getStructCache ^InterleavedIndexHopMap [this]"public")
  (writeTag ^FressianWriter [this] "public")
  (writeExt ^FressianWriter [this]"public")
  (writeCount [this n] "public")
  (bitSwitch ^int [this l] "private")
  ; (internalWriteInt [this i] "private")
  (shouldSkipCache ^boolean [this o] "private")
  (doWrite [this tag o w cache?] "private")
  (writeAs ^FressianWriter
           [this tag o]
           [this tag o cache?] "public")
  (writeObject ^FressianWriter
               [this o]
               [this o cache?] "public")
  (writeCode [this code] "public")
  (close [this] "public")
  (beginOpenList ^FressianWriter [this] "public")
  (beginClosedList ^FressianWriter [this] "public")
  (endList ^FressianWriter [this] "public"))


; (defn write-string [wtr s]
;   (let [max-buf-needed (min (* (count s) 3) 65536)
;         string-buffer (js/Int8Array. (js/ArrayBuffer. max-buf-needed))]
;     (loop [[string-pos buf-pos] (buffer-string-chunk-utf8 s 0 string-buffer)]
;       (if (< buf-pos (codes/ranges :string-packed-length-end)) ;8
;         (write-code wtr (+ codes/STRING_PACKED_LENGTH_START buf-pos))
;         (if (= string-pos (count s))
;           (do
;             (write-code wtr codes/STRING)
;             (write-count wtr buf-pos))
;           (do
;             (write-code wtr codes/STRING_CHUNK)
;             (write-int wtr buf-pos))))
;       (write-raw-bytes wtr string-buffer 0 buf-pos)
;       (when (< string-pos (count s))
;         (recur (buffer-string-chunk-utf8 s string-pos string-buffer)))))
;   wtr)

(defn bit-switch [l]
  (- 64 (.-length (.toString (.abs js/Math l) 2))))

(defn internalWriteInt [wtr n]
  (let [s (bit-switch n)
        raw (.-raw-out wtr)]
    (cond
      (<=  1 s 14)
      (do
        (writeCode wtr codes/INT)
        (rawOut/writeRawInt64 raw n))

      (<= 15 s 22)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_7_ZERO (>>> n 48)))
        (rawOut/writeRawInt48 raw n))

      (<= 23 s 30)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_6_ZERO (>>> n 40)))
        (rawOut/writeRawInt40 raw n))

      (<= 31 s 38)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_5_ZERO (>>> n 32)))
        (rawOut/writeRawInt32 raw n))

      (<= 39 s 44)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_4_ZERO (>>> n 24)))
        (rawOut/writeRawInt24 raw n))

      (<= 45 s 51)
      (do
        (rawOut/writeRawByte raw (+ codes/INT_PACKED_3_ZERO (>>> n 16)))
        (rawOut/writeRawInt16 raw n))

      (<= 52 s 57)
      (do
        (rawOut/writeRawByte raw (+ codesINT_PACKED_2_ZERO (>>> n 8)))
        (rawOut/writeRawByte raw  n))

      (<= 58 s 64)
      (do
        (when (< n -1)
          (rawOut/writeRawByte raw (+ codes/INT_PACKED_2_ZERO (>>> n 8))))
        (rawOut/writeRawByte raw n))

      :default
      (throw (js/Error. "more than 64 bits in a long!")))))



; implements StreamingWriter, Writer, Closeable
; out = output-stream
; raw-out = RawOutput
(defrecord FressianWriter [out raw-out priorityCache structCache sb handlers]
  IFressianWriter
  (writeCode [this code] (rawOut/writeRawByte raw-out code))

  (writeCount [this n] (writeInt this n))

  (writeNull [this] (writeCode this codes/NULL))

  (writeInt [this i]
    (if (nil? i)
      (do
        (writeNull this)
        this)
      (do
        (assert (int? i))
        (internalWriteInt this i)
        this)))

  (writeBytes [this bytes]
    (if (nil? bytes)
      (do
        (writeNull this)
        this)
      (writeBytes this bytes 0 (alength bytes))))

  (writeBytes [this bytes offset length]
    (assert (instance? js/Uint8Array bytes) "writeRawBytes expects a Int8Array")
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
            (rawOut/writeRawBytes raw-out bytes offset len)))))
    this)

  ; (writeInt [this i]
  ;   (if (nil? i)
  ;     (writeNull [this])
  ;     (do ; jvm makes sure its a long; jvm makes sure its a long
  ;       (assert (int? i) "writeInt expects an integer value")
  ;       (internalWriteInt this i))))

  ; (writeString [this s] (write-string this s))
  )



(def default-write-handlers {})

(defn Writer
  [out handlers]
  (let [handlers (merge default-write-handlers handlers)
        raw-out (rawOut/raw-output)]
    (FressianWriter. out raw-out nil nil nil handlers)))
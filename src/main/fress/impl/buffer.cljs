(ns fress.impl.buffer
  (:require [fress.util :as util]))

(defprotocol IBuffer
  (getByte [this index])
  (getBytes [this off length])
  (reset [this]))

(defprotocol IBufferReader
  (getBytesRead [this])
  (notifyBytesRead [this ^int count])
  (readUnsignedByte [this])
  (readSignedByte [this])
  (readUnsignedBytes [this length] "return unsigned byte view on memory")
  (readSignedBytes [this length] "return signed byte view on memory"))

(defprotocol IBufferWriter
  (getFreeCapacity [this] "remaining free bytes to write")
  (room? [this length])
  (getBytesWritten [this])
  (writeByte [this byte])
  (writeBytes [this bytes] [this bytes offset length])
  (notifyBytesWritten [this ^int count]))

(defprotocol IStreamingWriter
  (toByteArray [this] "get byte-array of current buffer contents. does not close.")
  (flushTo [this out]
           [this out offset]
    "write bytes to externally provided arraybuffer source at the given offset"))

;; wasm users must write single object
(deftype
  ^{:doc "An interface for reading bytes from a Uint8Array"}
  BufferReader
  [u8arr ^number backing-offset ^number bytesRead]
  IBuffer
  (reset [this]
    (do
      (set! (.-bytesRead this) 0)
      (set! (.-open? this) true)))
  IBufferReader
  (getBytesRead ^number [this] bytesRead)
  (notifyBytesRead [this ^number n] (set! (.-bytesRead this) (+ bytesRead n)))
  (readUnsignedByte ^number [this]
    (let [byte (aget u8arr (+ backing-offset bytesRead))]
      (if (undefined? byte)
        (throw (js/Error. "EOF"))
        (do
          (notifyBytesRead this 1)
          byte))))
  (readSignedByte ^number [this]
    (let [byteview (js/Int8Array. (.-buffer u8arr))
          byte (aget byteview (+ backing-offset bytesRead))]
      (if (undefined? byte)
        (throw (js/Error. "EOF"))
        (do
          (notifyBytesRead this 1)
          byte))))
  (readSignedBytes [this length]
    (let [bytes (js/Int8Array. (.-buffer u8arr) (+  backing-offset bytesRead) length)]
      (notifyBytesRead this length)
      bytes))
  (readUnsignedBytes [this  length]
    (let [bytes (js/Uint8Array. (.-buffer u8arr) (+ backing-offset bytesRead) length)]
      (notifyBytesRead this length)
      bytes)))

(deftype
  ^{:doc
    "Backed by a plain array, 'BytesOutputStream' grows as bytes are written,
     is only realized into an byte-array when close() is called.

     In future can use ArrayBuffer.transfer()"}
  BytesOutputStream [^array arr ^number bytesWritten]
  IDeref
  (-deref [this] (toByteArray this))
  IBuffer
  (reset [this] (set! (.-bytesWritten this) 0))
  IStreamingWriter
  (flushTo [this buf] (flushTo this buf 0))
  (flushTo [this buf ptr]
    (assert (some? (.-buffer buf)) "flushTo requires an arraybuffer backed typed-array")
    (assert (util/valid-pointer? ptr) (str "buffer/flushTo given invalid pointer:" (pr-str ptr)))
    (let [bytes (if (== (alength arr) bytesWritten)
                  arr
                  (.slice arr 0 bytesWritten))]
      (assert (= (alength bytes) bytesWritten))
      (.set buf bytes ptr)))
  (toByteArray [this]
    (if (== bytesWritten (alength arr))
      (js/Uint8Array. arr)
      (js/Uint8Array. (.slice arr 0 bytesWritten))))
  IBufferWriter
  (room? ^boolean [this _] true)
  (getBytesWritten ^number [this] bytesWritten)
  (notifyBytesWritten [this ^number n]
    (assert (int? n) "written byte count must be an int")
    (set! (.-bytesWritten this) (+ n bytesWritten)))
  (writeByte [this byte]
    (if (<= bytesWritten (alength arr))
      (aset arr bytesWritten byte)
      (.push arr byte))
    (notifyBytesWritten this 1))
  (writeBytes [this bytes]
    (loop [i 0]
      (when-let [byte (aget bytes i)]
        (writeByte this byte)
        (recur (inc i)))))
  (writeBytes [this bytes offset length]
    (loop [i offset]
      (if-let [byte (and (< (- i offset) length) (aget bytes i))]
        (do
          (writeByte this byte)
          (recur (inc i)))))))

(defn byte-stream [] (BytesOutputStream. #js[] 0))

(defn with-capacity [n] (BytesOutputStream. (make-array n) 0))

(deftype ^{:doc "used on fixed size buffers"}
  BufferWriter
  [backing ^number backing-offset ^number bytesWritten]
  IBuffer
  (reset [this] (set! (.-bytesWritten this) 0))
  (getByte [this index]
    (assert (and (int? index) (<= 0 index)))
    (aget (js/Uint8Array. (.. backing -buffer)) (+ backing-offset index)))
  (getBytes [this offset length]
    (js/Uint8Array. (.-buffer backing) (+ offset backing-offset) length))
  IBufferWriter
  (getFreeCapacity ^number [this] (- (.. backing -buffer -byteLength) backing-offset bytesWritten))
  (room? ^boolean [this length]
    (let [free (getFreeCapacity this)]
      (<= length free)))
  (getBytesWritten ^number [this] bytesWritten)
  (notifyBytesWritten [this ^number n]
    (assert (int? n) "written byte count must be an int")
    (set! (.-bytesWritten this) (+ n bytesWritten)))
  (writeByte [this byte]
    (if (room? this 1)
      (do
        (aset (js/Int8Array. (.. backing -buffer)) bytesWritten byte)
        (notifyBytesWritten this 1)
        this)
      (throw (js/Error. "BufferWriter out of room"))))
  (writeBytes [this bytes] (writeBytes this bytes 0 (alength bytes)))
  (writeBytes [this bytes ^number offset ^number length]
    (assert (int? length))
    (if (room? this length)
      (let [i8array (js/Int8Array. (.. backing  -buffer))]
        (.set i8array (.subarray bytes offset (+ offset length)) (+  bytesWritten backing-offset))
        (notifyBytesWritten this length)
        this)
      (throw (js/Error. "BufferWriter out of room")))))

(defn readable-buffer
  "Build a BufferReader over a collection of bytes."
  ([backing](readable-buffer backing 0))
  ([backing backing-offset]
   (cond
     (some? (.-buffer backing))
     (BufferReader. backing (or backing-offset 0) 0)

     (instance? js/ArrayBuffer backing)
     (readable-buffer (js/Uint8Array. backing) backing-offset)

     (implements? IBufferReader backing)
     backing

     (vector? backing)
     (readable-buffer (js/Uint8Array. (into-array backing)) backing-offset)

     (array? backing)
     (readable-buffer (js/Uint8Array. backing) backing-offset)

     (instance? BytesOutputStream backing)
     (readable-buffer (toByteArray backing) backing-offset)

     (instance? BufferWriter backing)
     (readable-buffer (.-backing backing) backing-offset)

     :else
     (throw
       (js/Error.
        (str "invalid input type " (type backing) " passed to readable-buffer.\n"
             "Input must be a typed array, array-buffer, or IBufferWriter instance"))))))

(defn writable-buffer
  "Build a BufferWriter over a typed-array. If nil, returns a BytesOutputStream."
  ([](writable-buffer nil nil))
  ([backing](writable-buffer backing 0))
  ([backing backing-offset]
   (cond
     (nil? backing)
     (byte-stream)

     (implements? IBufferWriter backing)
     backing

     (instance? js/ArrayBuffer backing)
     (writable-buffer (js/Int8Array. backing) backing-offset)

     (some? (.-buffer backing))
     (BufferWriter. backing (int (or backing-offset 0)) 0)

     :else
     (throw
       (js/Error.
        (str "invalid input type " (type backing) " passed to writable-buffer.\n"
             "Input must be a typed array, array-buffer, or nil"))))))
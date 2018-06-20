(ns
  ^{:doc
    "On the JVM fressian can write to an output stream and treat it as
     an infinite sink. The size of the bytes written is not fressian's concern.

     In javascript we do not have that luxury: without knowing the final byte
     length of objects ahead of time, we need to be able to handle:
      - if we run out of free space, we need to be able to request more
       - wasm memory is paged, easy!
       - plain javascript arraybuffers are fixed sized, need to alloc new array
         and copy over contents
     - if we have excess room we need to emulate a closed stream's 'EOF' behavior
     - we should respect the reference passed by the caller if possible
     - we should be able to receive a buffer and start writing at a designated offset"}
  fress.impl.buffer
  (:require [fress.util :as util :refer [dbg log]]))


(defprotocol IBuffer
  (getByte [this index])
  (getBytes [this off length])
  (reset [this]))

(defprotocol IReadableBuffer
  (getBytesRead [this])
  (notifyBytesRead [this ^int count])
  (readUnsignedByte [this])
  (readSignedByte [this])
  (readUnsignedBytes [this length] "return unsigned byte view on memory")
  (readSignedBytes [this length] "return signed byte view on memory"))

(defprotocol IWritableBuffer
  (getFreeCapacity [this] "remaining free bytes to write")
  (room? [this length])
  (getBytesWritten [this])
  (writeByte [this byte])
  (writeBytes [this bytes] [this bytes offset length])
  (notifyBytesWritten [this ^int count]))

(defprotocol IWritableStream
  (realize [this] "get byte-array of current buffer contents. does not close.")
  (close [this] "disable further writing, return byte-array")
  (flushTo [this out] [this out offset]
    "write bytes to externally provided arraybuffer source at the given offset"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; wasm users need to trigger EOF using footer or wrap everying in single object
;; add arity to readBytes for array to  copy into?
(deftype ReadableBuffer
  [memory ^number memory-offset ^number bytesRead]
  IBuffer
  (reset [this] (set! (.-bytesRead this) 0))
  IReadableBuffer
  (getBytesRead ^number [this] bytesRead)
  (notifyBytesRead [this ^number n]
    (set! (.-bytesRead this) (+ bytesRead n)))
  (readUnsignedByte ^number [this]
    (assert (and (int? bytesRead) (<= 0 bytesRead)))
    (let [byteview (js/Uint8Array. (.-buffer memory))
          byte (aget byteview (+ memory-offset bytesRead))]
      (when (or (neg? byte) (nil? byte)) (throw (js/Error. "EOF")))
      (notifyBytesRead this 1)
      byte))
  (readSignedByte ^number [this]
    (assert (and (int? bytesRead) (<= 0 bytesRead)))
    (let [byteview (js/Int8Array. (.-buffer memory))
          byte (aget byteview (+ memory-offset bytesRead))]
      (when (nil? byte) (throw (js/Error. "EOF")))
      (notifyBytesRead this 1)
      byte))
  (readSignedBytes [this  length]
    (assert (<= 0 (+ bytesRead length) (.. memory -buffer -byteLength)))
    (let [bytes (js/Int8Array. (.-buffer memory) (+  memory-offset bytesRead) length)]
      (notifyBytesRead this length)
      bytes))
  (readUnsignedBytes [this  length]
    (assert (<= 0 (+ bytesRead length) (.. memory -buffer -byteLength)))
    (let [bytes (js/Uint8Array. (.-buffer memory) (+  memory-offset bytesRead) length)]
      (notifyBytesRead this length)
      bytes)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Writable stream

(deftype
  ^{:doc
    "Backed by a plain array, 'WritableStream' grows as bytes are written,
     is only realized into an byte-array when close() is called.

     In future can use ArrayBuffer.transfer()"}
  WritableStream [arr ^number bytesWritten ^boolean open? buffer]
  IDeref
  (-deref [this] (or buffer (realize this)))
  IWritableStream
  (flushTo [this buf] (flush-to this buf 0))
  (flushTo [this buf off] ; typed array or memory. what about raw ArrayBuffers?
    (assert (some? (.-buffer buf)))
    (let [free (- (.. buf -buffer -byteLength) off)]
      (if-not (<= bytesWritten free)
        (throw (js/Error. "flush-to buffer is too small"))
        (let [i8array (js/Int8Array. (.. buf  -buffer))
              stop (alength arr)] ;do we need new view? is it faster to check if buf is ok as is?
          (assert (and (int? off) (<= 0 off)) "flush-to offset must be a positive integer")
          (loop [i 0]
            (when (< i stop)
              (aset i8array (+ i off) (aget arr i))
              (recur (inc i))))))))
  (realize [this]
    (if-not buffer
      (let [ta (js/Int8Array. arr)]
        (set! (.-buffer this) ta)
        ta)
      buffer))
  (close [this]
    (set! (.-open? this) false)
    (realize this))
  IWritableBuffer
  (room? ^boolean [this _] open?)
  (getBytesWritten ^number [this] bytesWritten)
  (notifyBytesWritten [this ^number n]
    (assert (int? n) "written byte count must be an int")
    (set! (.-bytesWritten this) (+ n bytesWritten)))
  (writeByte [this byte]
    (and open?
         (do
           (.push arr byte)
           (if (some? buffer) (set! (.-buffer this) nil))
           true)))
  (writeBytes [this bytes]
    (and open?
         (do
           (goog.array.extend arr bytes)
           (if (some? buffer) (set! (.-buffer this) nil))
           true)))
  (writeBytes [this bytes offset length]
    (and open?
         (do
           (goog.array.extend arr (.slice  bytes offset (+ offset length)))
           (if (some? buffer) (set! (.-buffer this) nil))
           true))))

(defn write-stream []
  (let [bytesWritten 0
        open? true
        buffer nil]
    (WritableStream. #js[] bytesWritten open? buffer)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Writable Buffer

(deftype WritableBuffer [memory ^number memory-offset ^number bytesWritten]
  IBuffer
  (reset [this] (set! (.-bytesWritten this) 0))
  (getByte [this index]
    (assert (and (int? index) (<= 0 index)))
    (aget (js/Int8Array. (.. memory -buffer)) (+ memory-offset index)))
  (getBytes [this offset length]
    (js/Int8Array. (.-buffer memory) (+ offset memory-offset) length))
  IWritableBuffer
  (getFreeCapacity ^number [this] (- (.. memory -buffer -byteLength) memory-offset bytesWritten))
  (room? ^boolean [this length]
    (let [free (getFreeCapacity this)]
      (<= length free)))
  (getBytesWritten ^number [this] bytesWritten)
  (notifyBytesWritten [this ^number n]
    (assert (int? n) "written byte count must be an int")
    (set! (.-bytesWritten this) (+ n bytesWritten)))
  (writeByte [this byte]
    (when-not (room? this 1)
      (if (some? (.-grow memory))
        (.grow memory 1)
        (throw (js/Error. "WritableBuffer out of room"))))
    (aset (js/Int8Array. (.. memory -buffer)) bytesWritten byte)
    (notifyBytesWritten this 1))
  (writeBytes [this bytes] (writeBytes this bytes 0 (alength bytes)))
  (writeBytes [this bytes ^number offset ^number length]
    (assert (int? length))
    (when-not (room? this length)
      (if (some? (.-grow memory))
        (let [bytes-needed length
              pages-needed (js/Math.ceil (/ bytes-needed 65535))]
          (.grow memory pages-needed))
        (throw (js/Error. "WritableBuffer out of room"))))
    (let [i8array (js/Int8Array. (.. memory  -buffer))]
      (.set i8array (.subarray bytes offset (+ offset length)) (+  bytesWritten memory-offset))
      (notifyBytesWritten this length)))) ;<- is there  a meaningful value to return? bytesWritten?

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn readable-buffer
  ([backing](readable-buffer backing 0))
  ([backing backing-offset]
   (if (implements? IReadableBuffer backing)
     backing
     (let [_(assert (some? (.-buffer backing)))
           backing-offset (or backing-offset 0)
           _(assert (int? backing-offset))
           bytesRead 0]
       (ReadableBuffer. backing backing-offset bytesRead)))))

(defn writable-buffer
  ([](writable-buffer nil nil))
  ([backing](writable-buffer backing 0))
  ([backing backing-offset]
   (cond
     (implements? IWritableBuffer backing)
     backing

     (and (some? backing) (some? (.-buffer backing)))
     (let [backing-offset (or backing-offset 0)
           _(assert (int? backing-offset))
           bytesWritten 0]
       (WritableBuffer. backing backing-offset bytesWritten))

     :else
     (write-stream))))
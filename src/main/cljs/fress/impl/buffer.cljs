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
     - we should be able to receive a buffer and start writing at a designated offset
     - support wasm memory, plain array buffers, node streams

    This probably should emulate java interfaces"}
  fress.impl.buffer
  (:require [fress.util :as util :refer [dbg log]]))


(defprotocol IBuffer
  (getByte [this index])
  (getBytes [this off length])
  (reset [this]))


(defprotocol IReadableBuffer
  (getBytesRead [this])
  (readUnsignedByte [this])
  (readSignedByte [this])
  (readUnsignedBytes [this length] "return unsigned byte view on memory")
  (readSignedBytes [this length] "return signed byte view on memory"))

 ;;<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< how to handle EOF??
;; wasm users need to trigger EOF, use footer or write -1 ? <<<<<<<<<<<<<<<
;; add arity to readBytes for array to  copy into?
(deftype ReadableBuffer
  [memory ^number memory-offset ^number bytesRead]
  IBuffer
  (reset [this] (set! (.-bytesRead this) 0))
  IReadableBuffer
  (getBytesRead ^number [this] bytesRead)
  (readUnsignedByte ^number [this]
    (assert (and (int? bytesRead) (<= 0 bytesRead)))
    (let [byteview (js/Uint8Array. (.-buffer memory))
          byte (aget byteview (+ memory-offset bytesRead))]
      (when (or (neg? byte) (nil? byte)) (throw (js/Error. "EOF")))
      (set! (.-bytesRead this) (inc bytesRead))
      byte))
  (readSignedByte ^number [this]
    (assert (and (int? bytesRead) (<= 0 bytesRead)))
    (let [byteview (js/Int8Array. (.-buffer memory))
          byte (aget byteview (+ memory-offset bytesRead))]
      (when (nil? byte) (throw (js/Error. "EOF")))
      (set! (.-bytesRead this) (inc bytesRead))
      byte))
  (readSignedBytes [this  length]
    (assert (<= 0 length (.-byteLength (.-buffer memory))))
    (let [bytes (js/Int8Array. (.-buffer memory) (+  memory-offset bytesRead) length)]
      (set! (.-bytesRead this) (+ bytesRead length))
      bytes))
  (readUnsignedBytes [this  length]
    (assert (<= 0 length (.-byteLength (.-buffer memory))))
    (let [bytes (js/Uint8Array. (.-buffer memory) (+  memory-offset bytesRead) length)]
      (set! (.-bytesRead this) (+ bytesRead length))
      bytes)))


(defn readable-buffer
  ([](readable-buffer (js/WebAssembly.Memory. #js{:initial 1}) 0))
  ([backing](readable-buffer backing 0))
  ([backing backing-offset]
   (let [backing (or backing (js/WebAssembly.Memory. #js{:initial 1}))
         _(assert (some? (.-buffer backing)))
         backing-offset (or backing-offset 0)
         _(assert (int? backing-offset))
         bytesRead 0]
     (ReadableBuffer. backing backing-offset bytesRead))))




(defprotocol IWritableBuffer
  (getFreeCapacity [this] "remaining free bytes to write")
  (getBytesWritten [this])
  (room? [this length])
  (grow [this bytes-needed])
  (writeByte [this byte])
  (writeBytes
   [this bytes]
   [this bytes offset length])
  (notifyBytesWritten [this ^int count])
  (close [this]))

(deftype WritableBuffer
  [memory ^number memory-offset ^number bytesWritten]

  IBuffer
  (reset [this] (set! (.-bytesWritten this) 0))
  (getByte [this index]
    (assert (and (int? index) (<= 0 index)))
    (aget (js/Int8Array. (.. memory -buffer)) (+ memory-offset index)))
  (getBytes [this offset length]
    (let [bytes (js/Int8Array. (.-buffer memory) (+ offset memory-offset) length)]
      bytes))

  IWritableBuffer
  (getFreeCapacity ^number [this] (- (.. memory -buffer -byteLength) memory-offset bytesWritten))

  (getBytesWritten ^number [this] bytesWritten)

  (room? ^boolean [this length]
    (let [free (getFreeCapacity this)]
      (< length free)))

  (grow [this bytes-needed]
    (let [pages-needed (js/Math.ceil (/ bytes-needed 65535))]
      (.grow memory pages-needed)))

  (notifyBytesWritten [this ^number n]
    (assert (int? n) "written byte count must be an int")
    (set! (.-bytesWritten this) (+ n bytesWritten)))

  (writeByte ^boolean [this byte]
    (when-not ^boolean  (room? this 1) (grow this 1))
    (aset (js/Int8Array. (.. memory -buffer)) bytesWritten byte)
    ; (adler/update! checksum byte)
    (notifyBytesWritten this 1)
    true)

  (writeBytes ^boolean [this bytes] (writeBytes this bytes 0 (alength bytes)))
  (writeBytes ^boolean [this bytes offset length]
    ;; we are assuming we have unbounded write access. not sure how this is going
    ;; to work from wasm side of things
    (assert (int? length))
    (when-not ^boolean  (room? this length) (grow this length))
    (let [i8array (js/Int8Array. (.. memory  -buffer))]
      (.set i8array (.subarray bytes offset (+ offset length)) (+  bytesWritten memory-offset))
      ; (adler/update! checksum bytes offset length)
      (notifyBytesWritten this length))
      true))

;;;figure out how to set this up statically if possible
(defn writable-buffer
  ([](writable-buffer (js/WebAssembly.Memory. #js{:initial 1}) 0))
  ([backing](writable-buffer backing 0))
  ([backing backing-offset]
   (let [backing (or backing (js/WebAssembly.Memory. #js{:initial 1}))
         _(assert (some? (.-buffer backing)))
         backing-offset (or backing-offset 0)
         _(assert (int? backing-offset))
         bytesWritten 0]
     (WritableBuffer. backing backing-offset bytesWritten))))
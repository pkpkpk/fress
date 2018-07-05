(ns fress.impl.buffer)

(defprotocol IBuffer
  (getByte [this index])
  (getBytes [this off length])
  (reset [this])
  (close [this]))

(defprotocol IBufferReader
  ; (close [this] "throw EOF on any further reads, even if room")
  (getBytesRead [this])
  (notifyBytesRead [this ^int count])
  (readUnsignedByte [this])
  (readSignedByte [this])
  (readUnsignedBytes [this length] "return unsigned byte view on memory")
  (readSignedBytes [this length] "return signed byte view on memory"))

; IWriter => ;https://dev.clojure.org/jira/browse/CLJS-2247
(defprotocol IBufferWriter
  (getFreeCapacity [this] "remaining free bytes to write")
  (room? [this length])
  (getBytesWritten [this])
  (writeByte [this byte])
  (writeBytes [this bytes] [this bytes offset length])
  (notifyBytesWritten [this ^int count]))

(defprotocol IStreamingWriter
  (toByteArray [this] "get byte-array of current buffer contents. does not close.")
  ; (close [this] "disable further writing, return byte-array")
  (flushTo [this out] [this out offset]
    "write bytes to externally provided arraybuffer source at the given offset")
  (wrap [this out] [this out offset]
    "The new buffer will be backed by the given byte array; that is,
     modifications to the buffer will cause the array to be modified and vice versa."))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; wasm users need to trigger EOF using footer or agree on single object
;; add arity to readBytes for array to  copy into?
(deftype BufferReader
  [memory ^number memory-offset ^number bytesRead ^boolean open?]
  IBuffer
  (reset [this]
    (do
      (set! (.-bytesRead this) 0)
      (set! (.-open? this) true)))
  (close [this]
    (set! (.-open? this) false))
  IBufferReader
  (getBytesRead ^number [this] bytesRead)
  (notifyBytesRead [this ^number n]
    (assert (and (int? bytesRead) (<= 0 bytesRead)))
    (set! (.-bytesRead this) (+ bytesRead n)))
  (readUnsignedByte ^number [this]
    (if open?
      (let [byteview (js/Uint8Array. (.-buffer memory))
            byte (aget byteview (+ memory-offset bytesRead))]
        (when (or (neg? byte) (nil? byte)) (throw (js/Error. "EOF")))
        (notifyBytesRead this 1)
        byte)
      (throw (js/Error. "EOF"))))
  (readSignedByte ^number [this]
    (if open?
      (let [byteview (js/Int8Array. (.-buffer memory))
            byte (aget byteview (+ memory-offset bytesRead))]
        (when (nil? byte) (throw (js/Error. "EOF")))
        (notifyBytesRead this 1)
        byte)
      (throw (js/Error. "EOF"))))
  (readSignedBytes [this  length]
    (if open?
      (do
        (assert (<= 0 (+ bytesRead length) (.. memory -buffer -byteLength)))
        (let [bytes (js/Int8Array. (.-buffer memory) (+  memory-offset bytesRead) length)]
          (notifyBytesRead this length)
          bytes))
      (throw (js/Error. "EOF"))))
  (readUnsignedBytes [this  length]
    (if open?
      (do
        (assert (<= 0 (+ bytesRead length) (.. memory -buffer -byteLength)))
        (let [bytes (js/Uint8Array. (.-buffer memory) (+  memory-offset bytesRead) length)]
          (notifyBytesRead this length)
          bytes))
        (throw (js/Error. "EOF")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Writable stream

(deftype
  ^{:doc
    "Backed by a plain array, 'BytesOutputStream' grows as bytes are written,
     is only toByteArrayd into an byte-array when close() is called.

     In future can use ArrayBuffer.transfer()"}
  BytesOutputStream [arr ^number bytesWritten ^boolean open? buffer]
  IDeref
  (-deref [this] (or buffer (toByteArray this)))
  IBuffer
  (reset [this]
    (set! (.-bytesWritten this) 0)
    (set! (.-buffer this) nil))
  (close [this]
    (set! (.-open? this) false)
    (toByteArray this))
  IStreamingWriter
  (wrap [this buf](wrap this buf 0))
  (wrap [this buf off]
    (let []
      ))
  (flushTo [this buf] (flushTo this buf 0))
  (flushTo [this buf off]
    (assert (some? (.-buffer buf)) "flushTo requires an arraybuffer backed byte-array")
    (let [free (- (.. buf -buffer -byteLength) off)]
      (if-not (<= bytesWritten free)
        (throw (js/Error. "flush-to buffer is too small"))
        (let [ba (if (= (type buf) js/Int8Array)
                   buf
                   (js/Int8Array. (.. buf -buffer)))]
          (assert (and (int? off) (<= 0 off)) "flush-to offset must be a positive integer")
          (loop [i 0]
            (when (< i bytesWritten)
              (aset ba (+ i off) (aget arr i))
              (recur (inc i))))))))
  (toByteArray [this]
    (or buffer
        (let [ba (if (== bytesWritten (alength arr))
                   (js/Int8Array. arr)
                   (let [ba (js/Int8Array. bytesWritten)]
                     (loop [i 0]
                       (when (< i bytesWritten)
                         (aset ba i (aget arr i))
                         (recur (inc i))))
                     ba))]
          (set! (.-buffer this) ba)
          ba)))
  IBufferWriter
  (room? ^boolean [this _] open?)
  (getBytesWritten ^number [this] bytesWritten)
  (notifyBytesWritten [this ^number n]
    (assert (int? n) "written byte count must be an int")
    (set! (.-bytesWritten this) (+ n bytesWritten)))
  (writeByte [this byte]
    (when open?
      (if (<= bytesWritten (alength arr))
        (aset arr bytesWritten byte)
        (.push arr byte))
      (notifyBytesWritten this 1)
      (set! (.-buffer this) nil)
      true))
  (writeBytes [this bytes]
    (when open?
      (loop [i 0]
        (when-let [byte (aget bytes i)]
          (if (< (+ i bytesWritten) (alength arr))
            (aset arr i byte)
            (.push arr byte))
          (recur (inc i))))
      (notifyBytesWritten this (alength bytes))
      (set! (.-buffer this) nil)
      true))
  (writeBytes [this bytes offset length]
    (when open?
      (loop [i offset]
        (if-let [byte (and (< (- i offset) length)
                             (aget bytes i))]
          (do
            (if (< (+ i bytesWritten) (alength arr))
              (aset arr i byte)
              (.push arr byte))
            (recur (inc i)))
          (notifyBytesWritten this (- i offset))))
      (set! (.-buffer this) nil)
      true)))

(defn byte-stream []
  (let [bytesWritten 0
        open? true
        buffer nil]
    (BytesOutputStream. #js[] bytesWritten open? buffer)))

(deftype
  ^{:doc "used on fixed size buffers"}
  BufferWriter [memory ^number memory-offset ^number bytesWritten]
  IBuffer
  (reset [this] (set! (.-bytesWritten this) 0))
  (getByte [this index]
    (assert (and (int? index) (<= 0 index)))
    (aget (js/Int8Array. (.. memory -buffer)) (+ memory-offset index)))
  (getBytes [this offset length]
    (js/Int8Array. (.-buffer memory) (+ offset memory-offset) length))
  IBufferWriter
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
        (throw (js/Error. "BufferWriter out of room"))))
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
        (throw (js/Error. "BufferWriter out of room"))))
    (let [i8array (js/Int8Array. (.. memory  -buffer))]
      (.set i8array (.subarray bytes offset (+ offset length)) (+  bytesWritten memory-offset))
      (notifyBytesWritten this length)))) ;<- is there  a meaningful value to return? bytesWritten? this?

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn readable-buffer
  ([backing](readable-buffer backing 0))
  ([backing backing-offset]
   (cond
     (implements? IBufferReader backing)
     backing

     (instance? BytesOutputStream backing)
     (readable-buffer (toByteArray backing) backing-offset)

     (instance? BufferWriter backing)
     (readable-buffer (.-memory backing) backing-offset)

     (instance? js/ArrayBuffer backing)
     (readable-buffer (js/Int8Array. backing) backing-offset)

     (some? (.-buffer backing))
     (BufferReader. backing (int (or backing-offset 0)) 0 true)

     :else
     (throw
       (js/Error.
        (str "invalid input type " (type backing) " passed to readable-buffer.\n"
             "Input must be a typed array, array-buffer, or IBufferWriter instance"))))))

(defn writable-buffer
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
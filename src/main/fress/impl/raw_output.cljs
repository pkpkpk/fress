(ns fress.impl.raw-output
  (:require-macros [fress.macros :refer [>>>]])
  (:require [fress.adler32 :as adler]
            [goog.string :as gstring]))

(def isBigEndian
  (-> (.-buffer (js/Uint32Array. #js[0x12345678]))
    (js/Uint8Array. )
    (aget 0)
    (== 0x12)))

(defprotocol IRawOutput
  (getByte [this index] "returns nil on oob")
  (getMemory [this])
  (notifyBytesWritten [this ^int count])
  (getBytesWritten [this])
  (writeRawByte [this b] "pub")
  (writeRawBytes [this bs off len])
  (writeRawInt16 [this i])
  (writeRawInt24 [this i])
  (writeRawInt32 [this i])
  (writeRawInt40 [this i])
  (writeRawInt48 [this i])
  (writeRawInt64 [this i])

  (writeRawFloat [this i])
  (writeRawDouble [this i])
  (getChecksum [this])
  (reset [this]))

(deftype RawOutput [memory ^number bytesWritten checksum]
  IRawOutput
  (getChecksum [this] (adler/get-value checksum))

  (reset [this]
    (set! (.-bytesWritten this) 0)
    (adler/reset checksum))

  (getByte [this ^number index] ;?int
    (assert (and (int? index) (<= 0 index)))
    (when (< index bytesWritten)
      ; (let [view (js/DataView. (.. memory -buffer))]
      ;   (.getInt8 view index))
      (aget (js/Int8Array. (.. memory -buffer)) index)))

  (getMemory [this] memory)

  (getBytesWritten ^number [this] bytesWritten)

  (notifyBytesWritten [this ^number n]
    (assert (int? n) "written byte count must be an int")
    (set! (.-bytesWritten this) (+ n bytesWritten)))

  (writeRawByte [this ^number byte]; packing ints requires letting some bytes roll
    (when (< (.. memory -buffer -byteLength) (+ bytesWritten len))
      (.grow memory 1))
    (aset (js/Int8Array. (.. memory -buffer)) bytesWritten byte)
    (adler/update! checksum byte)
    (notifyBytesWritten this 1))

  (writeRawBytes [this bytes offset len]
    (when (< (.. memory -buffer -byteLength) (+ bytesWritten len))
      (let [byte-diff (- (+ bytesWritten len) (.. memory -buffer -byteLength))
            pages-needed (js/Math.ceil (/ byte-diff 65535))]
        (.grow memory pages-needed)))
    (let [i8array (js/Int8Array. (.. memory  -buffer))]
      (.set i8array (.subarray bytes offset (+ offset len)) bytesWritten)
      (adler/update! checksum bytes offset len)
      (notifyBytesWritten this len)))

  (writeRawInt16 [this i]
    (writeRawByte this (bit-and (>>> i 8) 0xFF))
    (writeRawByte this (bit-and i 0xFF)))

  (writeRawInt24 [this i]
    (writeRawByte this (bit-and (>>> i 16) 0xFF))
    (writeRawByte this (bit-and (>>> i 8) 0xFF))
    (writeRawByte this (bit-and i 0xFF)))

  (writeRawInt32 [this i]
    (writeRawByte this (bit-and (>>> i 24) 0xFF))
    (writeRawByte this (bit-and (>>> i 16) 0xFF))
    (writeRawByte this (bit-and (>>> i 8) 0xFF))
    (writeRawByte this (bit-and i 0xFF)))

  (writeRawInt40 [this i]
    (writeRawByte this (bit-and (>>> i 32) 0xFF))
    (writeRawByte this (bit-and (>>> i 24) 0xFF))
    (writeRawByte this (bit-and (>>> i 16) 0xFF))
    (writeRawByte this (bit-and (>>> i 8) 0xFF))
    (writeRawByte this (bit-and i 0xFF)))

  (writeRawInt48 [this i]
    (writeRawByte this (bit-and (>>> i 40) 0xFF))
    (writeRawByte this (bit-and (>>> i 32) 0xFF))
    (writeRawByte this (bit-and (>>> i 24) 0xFF))
    (writeRawByte this (bit-and (>>> i 16) 0xFF))
    (writeRawByte this (bit-and (>>> i 8) 0xFF))
    (writeRawByte this (bit-and i 0xFF)))

  ; (writeRawInt64 [this i]
  ;   (writeRawByte this (bit-and (>>> i 56) 0xFF))
  ;   (writeRawByte this (bit-and (>>> i 48) 0xFF))
  ;   (writeRawByte this (bit-and (>>> i 40) 0xFF))
  ;   (writeRawByte this (bit-and (>>> i 32) 0xFF))
  ;   (writeRawByte this (bit-and (>>> i 24) 0xFF))
  ;   (writeRawByte this (bit-and (>>> i 16) 0xFF))
  ;   (writeRawByte this (bit-and (>>> i 8) 0xFF))
  ;   (writeRawByte this (bit-and i 0xFF)))

  (writeRawInt64 [this i]
    (dotimes [x 8]
      (writeRawByte this (>>> i (* (- 7 x) 8)))))

  (writeRawFloat [this f]
    (let [f32array (js/Float32Array. 1)]
      (aset f32array 0 f)
      (let [bytes (js/Int8Array. (.-buffer f32array))]
        (if ^boolean isBigEndian
          (writeRawBytes this bytes 0 (alength bytes))
          (writeRawBytes this (.reverse bytes) 0 (alength bytes))))))

  (writeRawDouble [this f]
    (let [f64array (js/Float64Array. 1)]
      (aset f64array 0 f)
      (let [bytes (js/Int8Array. (. f64array -buffer))]
        (if ^boolean isBigEndian
          (writeRawBytes this bytes 0 (alength bytes))
          (writeRawBytes this (.reverse bytes) 0 (alength bytes)))))))

(defn raw-output []
  (let [memory (js/WebAssembly.Memory. #js{:initial 1})
        bytesWritten 0]
    (RawOutput. memory bytesWritten (adler/adler32))))


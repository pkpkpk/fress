(ns fress.impl.raw-input
  (:require [fress.adler32 :as adler]))


(defprotocol IRawInput
  (readRawByte [this])
  (readRawInt8 [this])
  (readRawInt16 [this])
  (readRawInt24 [this])
  (readRawInt32 [this])
  (readRawInt40 [this])
  (readRawInt48 [this])
  (readRawInt64 [this])
  (readRawFloat [this])
  (readRawDouble [this])
  (readFully [this length]
             #_[this bytes offset length])
  (getBytesRead [this])
  (reset [this])
  (validateChecksum [this]))

(defrecord RawInput [memory bytesRead checksum]
  IRawInput
  (getBytesRead ^number [this] bytesRead)
  (readRawByte ^number [this]
    (assert (and (int? bytesRead) (<= 0 bytesRead)))
    (let [; val (.getInt8 (js/DataView. (.. memory -buffer)) bytesRead)
          val (aget (js/Int8Array. (.. memory -buffer)) bytesRead)]
      ; (if (< val 0) (throw (js/Error. "EOF")))
      (set! (.-bytesRead this) (inc bytesRead))
      val))
  (readRawInt8 ^number [this] (readRawByte this))
  (readRawInt16 ^number [this]
    (+ (bit-shift-left (readRawByte this) 8)
       (readRawByte this)))
  (readRawInt24 ^number [this]
    (+ (bit-shift-left (readRawByte this) 16)
       (bit-shift-left (readRawByte this) 8)
       (readRawByte this)))
  (readRawInt32 ^number [this] ;=> reads 4 bytes
    (+ (bit-shift-left (readRawByte this) 24)
       (bit-shift-left (readRawByte this) 16)
       (bit-shift-left (readRawByte this) 8)
       (readRawByte this)))
  (readRawInt40 ^number [this]
    (+ (<< (read-raw-byte this) 32)
       (readRawInt32 this)))
  (readRawInt48 ^number [this]
    (+ (<< (read-raw-byte this) 40)
       (readRawInt40 this)))
  (readRawInt64 ^number [this] ;=> goog.math.Long???
    (+ (<< (read-raw-byte this) 56)
       (<< (read-raw-byte this) 48)
       (readRawInt48 this)))
  (readRawFloat ^number [this] ;=> reads 4 bytes
    (let [f32buf (js/Float32Array. 1)
          u8buf  (js/Uint8Array. (. f32buf -buffer))]
      (dotimes [i 4]
        (let [b (readRawByte this)]
          (aset u8buf i b)))
      (aget f32buf 0)))
  (readRawDouble ^number [this] ;=> reads 8 bytes
    (let [buf (js/ArrayBuffer. 8)
          h (readRawInt32 this)
          l (readRawInt32 this)]
      (aset (js/Int32Array. buf) 0 h)
      (aset (js/Int32Array. buf) 1 l)
      (aget (js/Float64Array. buf) 0)))
  (readFully [this length] ;; need to clamp somehow so we dont read past end of written
    (let [bytes (js/Int8Array. memory bytesRead length)]
      (set! (.-bytesRead this) (+ bytesRead length))
      bytes))
  (reset [this]
    (set! (.-bytesRead this) 0)
    (when checksum
      (adler/reset checksum)))
  (validateChecksum [this]
    (if (nil? checksum)
      (readRawInt32 this)
      (let [calculatedChecksum (adler/get-value checksum)
            receivedChecksum (readRawInt32 this)]
        (if (not= calculatedChecksum receivedChecksum)
          (throw
            (js/Error. "Invalid footer checksum, expected " calculatedChecksum" got " receivedChecksum)))))))


(defn raw-input
  ([memory](raw-input memory 0))
  ([memory start-index](raw-input memory start-index true))
  ([memory ^number start-index ^boolean validateAdler]
   ; (if validateAdler )
   (RawInput. memory start-index (adler/adler32))))
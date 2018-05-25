(ns fress.impl.raw-input
  (:require-macros [fress.macros :refer [<< >>>]])
  (:require [fress.adler32 :as adler])
  (:import [goog.math Long]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

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

;; need to clamp somehow so we dont read past end of written
;; need to clamp somehow so we dont read past end of written
;; need to clamp somehow so we dont read past end of written
;; need to clamp somehow so we dont read past end of written

(def ^:dynamic *throw-on-unsafe?* true)

(defrecord RawInput [memory bytesRead checksum]
  IRawInput
  (getBytesRead ^number [this] bytesRead)
  (readRawByte ^number [this]
    (assert (and (int? bytesRead) (<= 0 bytesRead)))
    (let [; val (.getInt8 (js/DataView. (.. memory -buffer)) bytesRead)
          ; val (aget (js/Int8Array. (.. memory -buffer)) bytesRead)
          val (aget (js/Uint8Array. (.. memory -buffer)) bytesRead)]
      (if (< val 0) (throw (js/Error. "EOF"))) ;nil?
      (set! (.-bytesRead this) (inc bytesRead))
      val))
  (readRawInt8 ^number [this] (readRawByte this))

  (readRawInt16 ^number [this]
    (let [high (readRawByte this)
          low  (readRawByte this)]
      (+ (bit-shift-left high 8) low)))

  (readRawInt24 ^number [this]
    (+ (bit-shift-left (readRawByte this) 16)
       (bit-shift-left (readRawByte this) 8)
       (readRawByte this)))

  (readRawInt32 ^number [this]
    (+ (bit-shift-left (readRawByte this) 24)
       (bit-shift-left (readRawByte this) 16)
       (bit-shift-left (readRawByte this) 8)
       (readRawByte this)))

  (readRawInt40 ^number [this]
    (let [high (Long.fromNumber (readRawByte this))
          low (Long.fromNumber (readRawInt32 this))]
      (.toNumber (.add (.shiftLeft high 32) low))))

  (readRawInt48 ^number [this]
    (let [high (Long.fromNumber (readRawByte this))
          low (Long.fromNumber (readRawInt40 this))]
      (.toNumber (.add (.shiftLeft high 40) low))))

  (readRawInt64 ^number [this]
    (let [a (readRawByte this)
          b (readRawByte this)
          c (readRawByte this)
          d (readRawByte this)
          e (readRawByte this)
          f (readRawByte this)
          g (readRawByte this)
          h (readRawByte this)]
      (when *throw-on-unsafe?*
        (if (<= a 127)
          (when (or (<= 32 b) (< 1561 (+ b c d e f g h)))
            (throw (js/Error. (str  "i64 at byte index " bytesRead " exceeds javascript's Number.MAX_SAFE_INTEGER"))))
          (when (or (< a 255) (< b 224) (zero? h) )
            (throw (js/Error. (str  "i64 at byte index " bytesRead " exceeds javascript's Number.MIN_SAFE_INTEGER"))))))
      (let [x (Long.fromNumber 0xff)
            a  (.and (Long.fromNumber a) x)
            b  (.and (Long.fromNumber b) x)
            c  (.and (Long.fromNumber c) x)
            d  (.and (Long.fromNumber d) x)
            e  (.and (Long.fromNumber e) x)
            f  (.and (Long.fromNumber f) x)
            g  (.and (Long.fromNumber g) x)
            h  (.and (Long.fromNumber h) x)]
        (-> (.shiftLeft a 56)
            (.or (.shiftLeft b 48))
            (.or (.shiftLeft c 40))
            (.or (.shiftLeft d 32))
            (.or (.shiftLeft e 24))
            (.or (.shiftLeft f 16))
            (.or (.shiftLeft g 8))
            (.or h)
            (.toNumber)))))

  (readRawFloat ^number [this]
    (let [f32buf (js/Float32Array. 1)
          u8buf  (js/Uint8Array. (. f32buf -buffer))]
      (dotimes [i 4]
        (let [b (readRawByte this)]
          (aset u8buf i b)))
      (aget f32buf 0)))
  (readRawDouble ^number [this]
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
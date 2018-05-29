(ns fress.impl.raw-input
  (:require-macros [fress.macros :refer [<< >>>]])
  (:require [fress.adler32 :as adler]
            [fress.util :refer [isBigEndian]])
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

(def ^:dynamic *throw-on-unsafe?* true)

(def L0xff (Long.fromNumber 0xff))
(def L0xffffffff (Long.fromNumber 0xffffffff))

(defn ^Long readRawInt32L [this]
  (let [a (.and (Long.fromNumber (readRawByte this)) L0xff)
        b (.and (Long.fromNumber (readRawByte this)) L0xff)
        c (.and (Long.fromNumber (readRawByte this)) L0xff)
        d (.and (Long.fromNumber (readRawByte this)) L0xff)]
    (-> (.shiftLeft a 24)
        (.or (.shiftLeft b 16))
        (.or (.shiftLeft c 8))
        (.or d)
        (.and L0xffffffff))))

(defn ^Long readRawInt40L [this]
  (let [high (Long.fromNumber (readRawByte this))
        low (readRawInt32L this)]
    (.add (.shiftLeft high 32) low)))

(defn ^Long readRawInt48L [this]
  (let [high (Long.fromNumber (readRawByte this))
        low (readRawInt40L this)]
    (.add (.shiftLeft high 40) low)))

(defn ^Long readRawInt64L [this]
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
          (throw (js/Error. (str  "i64 at byte index " bytesRead " exceeds js/Number.MAX_SAFE_INTEGER"))))
        (when (or (< a 255) (< b 224) (zero? h) )
          (throw (js/Error. (str  "i64 at byte index " bytesRead " exceeds js/Number.MIN_SAFE_INTEGER"))))))
    (let [a  (.and (Long.fromNumber a) L0xff)
          b  (.and (Long.fromNumber b) L0xff)
          c  (.and (Long.fromNumber c) L0xff)
          d  (.and (Long.fromNumber d) L0xff)
          e  (.and (Long.fromNumber e) L0xff)
          f  (.and (Long.fromNumber f) L0xff)
          g  (.and (Long.fromNumber g) L0xff)
          h  (.and (Long.fromNumber h) L0xff)]
      (-> (.shiftLeft a 56)
          (.or (.shiftLeft b 48))
          (.or (.shiftLeft c 40))
          (.or (.shiftLeft d 32))
          (.or (.shiftLeft e 24))
          (.or (.shiftLeft f 16))
          (.or (.shiftLeft g 8))
          (.or h)))))

(defrecord RawInput [memory bytesRead checksum]
  IRawInput
  (getBytesRead ^number [this] bytesRead)

  (readRawByte ^number [this]
    (assert (and (int? bytesRead) (<= 0 bytesRead)))
    (let [; val (.getInt8 (js/DataView. (.. memory -buffer)) bytesRead)
          ; val (aget (js/Int8Array. (.. memory -buffer)) bytesRead)
          ;; need to clamp somehow so we dont read past end of written
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

  (readRawInt32 ^number [this] (.toNumber (readRawInt32L this)))

  (readRawInt40 ^number [this] (.toNumber (readRawInt40L this)))

  (readRawInt48 ^number [this] (.toNumber (readRawInt48L this)))

  (readRawInt64 ^number [this] (.toNumber (readRawInt64L this)))

  (readRawFloat ^number [this]
    (let [bytes (js/Int8Array. 4)]
      (dotimes [i 4]
        (let [i (if isBigEndian i (- 3 i))]
          (aset bytes i (readRawByte this))))
      (aget (js/Float32Array. (.-buffer bytes)) 0)))

  (readRawDouble ^number [this]
    (let [bytes (js/Int8Array. 8)]
      (dotimes [i 8]
        (let [i (if isBigEndian i (- 7 i))]
          (aset bytes i (readRawByte this))))
      (aget (js/Float64Array. (.-buffer bytes)) 0)))

  (readFully [this length]
    (assert (<= 0 length))
    (assert (<= length (.-byteLength (.-buffer memory))))
    ;; need to clamp somehow so we dont read past end of written
    ;; lost an arity here, give another look
    (let [bytes (js/Int8Array. (.-buffer memory) bytesRead length)]
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
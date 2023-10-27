(ns fress.impl.raw-input
  (:require-macros [fress.macros :refer [<< >>>]])
  (:require [fress.impl.adler32 :as adler]
            [fress.impl.buffer :as buf]
            [fress.util :as util :refer [isBigEndian log dbg]])
  (:import [goog.math Long]))

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
  (close [this] "throw EOF on any further reads, even if room")
  (validateChecksum [this]))

(def L_U8_MAX_VALUE (Long.fromNumber util/u8_MAX_VALUE))
(def L_U32_MAX_VALUE (Long.fromNumber util/u32_MAX_VALUE))

(defn ^Long readRawInt32L [this]
  (let [a (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        b (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        c (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        d (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)]
    (-> (.shiftLeft a 24)
        (.or (.shiftLeft b 16))
        (.or (.shiftLeft c 8))
        (.or d)
        (.and L_U32_MAX_VALUE))))

(defn ^Long readRawInt40L [this]
  (let [high (Long.fromNumber (readRawByte this))
        low (readRawInt32L this)]
    (.add (.shiftLeft high 32) low)))

(defn ^Long readRawInt48L [this]
  (let [high (Long.fromNumber (readRawByte this))
        low (readRawInt40L this)]
    (.add (.shiftLeft high 40) low)))

(defn ^Long readRawInt64L [this]
  (let [a  (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        b  (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        c  (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        d  (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        e  (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        f  (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        g  (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)
        h  (.and (Long.fromNumber (readRawByte this)) L_U8_MAX_VALUE)]
    (-> (.shiftLeft a 56)
      (.or (.shiftLeft b 48))
      (.or (.shiftLeft c 40))
      (.or (.shiftLeft d 32))
      (.or (.shiftLeft e 24))
      (.or (.shiftLeft f 16))
      (.or (.shiftLeft g 8))
      (.or h))))

(defrecord RawInput [in checksum]
  IRawInput
  (getBytesRead ^number [this] (buf/getBytesRead in))

  (readRawByte [this]
    (let [byte (buf/readUnsignedByte in)]
      (when checksum (adler/update! checksum byte))
      byte))

  (readFully [this length] ;=> signed-byte-array
    ;; need arity to provides byte-array destination
    (let [bytes (buf/readSignedBytes in length)]
      (when checksum (adler/update! checksum bytes 0 length))
      bytes))

  (reset [this]
    (buf/reset in)
    (when checksum (adler/reset checksum)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

  (readRawInt8 ^number [this] (readRawByte this))

  (readRawInt16 ^number [this]
    (let [high (readRawByte this)
          low  (readRawByte this)]
      (+ (<< high 8) low)))

  (readRawInt24 ^number [this]
    (+ (<< (readRawByte this) 16)
       (<< (readRawByte this) 8)
       (readRawByte this)))

  (readRawInt32 ^number [this] (.toNumber (readRawInt32L this)))

  (readRawInt40 ^number [this] (.toNumber (readRawInt40L this)))

  (readRawInt48 ^number [this] (.toNumber (readRawInt48L this)))

  (readRawInt64 ^number [this] (.toNumber (readRawInt64L this)))

  (readRawFloat ^number [this]
    (let [bytes (js/Int8Array. 4)]
      (dotimes [i 4]
        (let [i (if ^boolean isBigEndian i (- 3 i))]
          (aset bytes i (readRawByte this))))
      (aget (js/Float32Array. (.-buffer bytes)) 0)))

  (readRawDouble ^number [this]
    (let [bytes (js/Int8Array. 8)]
      (dotimes [i 8]
        (let [i (if ^boolean isBigEndian i (- 7 i))]
          (aset bytes i (readRawByte this))))
      (aget (js/Float64Array. (.-buffer bytes)) 0)))

  (validateChecksum [this]
    (if (nil? checksum)
      (readRawInt32 this)
      (let [calculatedChecksum @checksum
            receivedChecksum (readRawInt32 this)]
        (if (not= calculatedChecksum receivedChecksum)
          (throw
            (js/Error. "Invalid footer checksum, expected " calculatedChecksum" got " receivedChecksum)))))))

(defn raw-input
  ([in](raw-input in 0))
  ([in start-index](raw-input in start-index true))
  ([in ^number start-index ^boolean validateAdler]
   (let [in (buf/readable-buffer in start-index)]
     (RawInput. in (if validateAdler (adler/adler32))))))

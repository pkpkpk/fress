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
  (readFully [this bytes offset length])
  (getBytesRead [this])
  (reset [this])
  (validateChecksum [this])
  )

(defrecord RawInput [memory index checksum]
  IRawInput

  (readRawByte [this]
    (assert (and (int? index) (<= 0 index)))
    (let [view (js/DataView. (.. memory -buffer))
          val (.getInt8 view index)]
      (set! (.-index this) (inc index))
      val))
  )


(defn raw-input
  ([memory](raw-input memory 0))
  ([memory start-index](raw-input memory start-index true))
  ([memory ^number start-index ^boolean validateAdler]
   ; (if validateAdler )
   (RawInput. memory start-index (adler/adler32))))
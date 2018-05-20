(ns fress.raw-output
  (:require [fress.adler32 :as adler]
            [goog.string :as gstring]))

(defn ^boolean valid-byte? [n]
  (and (int? n) (<= 0 n) (< n 255)))

(defn- >>> [n s] ;=> macro
  (.floor js/Math (/ n (.pow js/Math 2 s))))

(defprotocol IRawOutput
  (?getByte [this index] "returns nil on oob")
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

(deftype RawOutput [buffer ^number bytesWritten checksum]
  IRawOutput
  (getChecksum [this] (adler/get-value checksum))

  (reset [this]
    (set! (.-bytesWritten this) 0)
    (adler/reset checksum))

  (?getByte [this ^number index] ;?int
    (assert (and (int? index) (<= 0 index)))
    (when (< index bytesWritten)
      (aget (js/Uint8Array. buffer) index)))

  (getBytesWritten ^number [this] bytesWritten)

  (notifyBytesWritten [this ^number n]
    (assert (int? n) "written byte count must be an int")
    (set! (.-bytesWritten this) (+ n bytesWritten)))

  (writeRawByte [this ^number byte]
    (assert (valid-byte? byte) "writeRawByte expects a valid byte")
    (aset (js/Uint8Array. buffer) bytesWritten byte)
    (adler/update! checksum byte)
    (notifyBytesWritten this 1))

  (writeRawBytes [this bytes offset len]
    (let [i8array (js/Uint8Array. buffer)]
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
      (let [bytes (js/Uint8Array. (. f32array -buffer))]
        (dotimes [i 4]
          (writeRawByte this (aget bytes i))))))

  (writeRawDouble [this f]
    (let [f64array (js/Float64Array. 1)]
      (aset f64array 0 f)
      (let [bytes (js/Uint8Array. (. f64array -buffer))]
        (dotimes [i 8]
          (writeRawByte this (aget bytes i)))))))

(defn raw-output []
  (let [buffer (js/ArrayBuffer. 65536)
        bytesWritten 0]
    (RawOutput. buffer bytesWritten (adler/adler32))))


(ns fress.raw-output
  (:require [fress.adler32 :as adler]
            [goog.string :as gstring]))

(defn ^boolean valid-byte? [n]
  (and (int? n) (<= 0 n) (< n 255)))


(defprotocol IRawOutput
  (?getByte [this index] "returns nil on oob")
  (notifyBytesWritten [this ^int count])
  (getBytesWritten [this])
  (writeRawByte [this b] "pub")
  (writeRawBytes [this bs off len]))

(deftype RawOutput [buffer ^number bytesWritten]
  IRawOutput
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
    ; (adler/update! (:checksum @wtr) b)
    (notifyBytesWritten this 1))
  (writeRawBytes [this bytes offset len]
    (assert (instance? js/Uint8Array bytes) "writeRawBytes expects a Int8Array")
    (let [i8array (js/Uint8Array. buffer)]
      (.set i8array (.subarray bytes offset (+ offset len)) bytesWritten)
      ; (adler/update! (:checksum @wtr) b off len)
      (notifyBytesWritten this len))))

(defn raw-output []
  (let [buffer (js/ArrayBuffer. 65536)
        bytesWritten 0]
    (RawOutput. buffer bytesWritten)))


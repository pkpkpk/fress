(ns fress.impl.raw-output
  (:require-macros [fress.macros :refer [>>>]])
  (:require [fress.impl.adler32 :as adler]
            [fress.util :refer [isBigEndian log dbg]]
            [fress.impl.buffer :as buf]
            [fress.impl.adler32 :as adler]
            [goog.string :as gstring]))

(defprotocol IRawOutput
  (getByte [this index])
  (getBytesWritten [this])
  (writeRawByte [this b])
  (writeRawBytes
   [this bytes]
   [this bs off len])
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

(deftype RawOutput [out checksum]
  IRawOutput
  (getChecksum [this] (adler/get-value checksum))

  (reset [this]
    (buf/reset out)
    (adler/reset checksum))

  (getByte [this ^number index] (buf/getByte out index))

  (getBytesWritten ^number [this] (buf/getBytesWritten out))

  (writeRawByte [this byte]
    (buf/writeByte out byte)
    (adler/update! checksum byte))

  (writeRawBytes [this bytes]
    (buf/writeBytes out bytes)
    (adler/update! checksum bytes 0 (alength bytes)))

  (writeRawBytes [this bytes offset length]
    (buf/writeBytes out bytes offset length)
    (adler/update! checksum bytes offset length))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn raw-output
  ([](raw-output nil ))
  ([out] (raw-output out 0))
  ([out offset]
   (let [
          ; out (if (nil? out)
          ;      (buf/writable-buffer)
          ;      (if (implements? buf/IWritableBuffer out)
          ;        out
          ;        (buf/writable-buffer out offset)))
         out (buf/writable-buffer out offset)
         checksum (adler/adler32)]
     (RawOutput. out checksum))))


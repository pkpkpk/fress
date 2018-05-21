(ns fress.writer-test
  (:require-macros [fress.macros :refer [>>>]])
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.raw-output :as rawOut]
            [fress.codes :as codes]
            [fress.ranges :as ranges]
            [fress.writer :as w]))

(defn is=
  ([a b] (is (= a b)))
  ([a b c] (is (= a b c)))
  ([a b c d] (is (= a b c d))))

; (defn getByte [wrtr i]
;   (rawOut/?getByte (.-raw-out wrtr) i))

(deftest writeBytes-test
  ; [(bytes.length + BYTES_PACKED_LENGTH_START ) , bytes[0], bytes[1],... ]
  (testing "(< length ranges/BYTES_PACKED_LENGTH_END)"
    (let [nums (range 0 7)
          bytes (js/Uint8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (< (alength bytes) ranges/BYTES_PACKED_LENGTH_END))
      (w/writeBytes wrtr bytes)
      (is= (w/getByte wrtr 0) (+ (alength bytes) codes/BYTES_PACKED_LENGTH_START))
      (doseq [n nums]
        (is= n (w/getByte wrtr (inc n))))))
  (testing "ranges/BYTES_PACKED_LENGTH_END < length < ranges/BYTE_CHUNK_SIZE"
    (let [nums (range 0 15)
          bytes (js/Uint8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (< ranges/BYTES_PACKED_LENGTH_END (alength bytes) ranges/BYTE_CHUNK_SIZE))
      (w/writeBytes wrtr bytes)
      (is= (w/getByte wrtr 0) codes/BYTES)
      (is= (w/getByte wrtr 1) (alength bytes))
      (doseq [n nums]
        (is= n (w/getByte wrtr (+ 2 n))))))
  (testing "ranges/BYTES_PACKED_LENGTH_END < ranges/BYTE_CHUNK_SIZE < length"
    (let [n 65537
          chunks (js/Math.floor (/ n ranges/BYTE_CHUNK_SIZE))
          remainder (mod n ranges/BYTE_CHUNK_SIZE)
          nums (take n (repeat 99))
          bytes (js/Uint8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (< ranges/BYTES_PACKED_LENGTH_END ranges/BYTE_CHUNK_SIZE (alength bytes)))
      (is= chunks 1)
      (is= remainder 2)
      (w/writeBytes wrtr bytes)
      (testing "write chunk"
        (is= (w/getByte wrtr 0) codes/BYTES_CHUNK)
        (testing "writeCount -> writeInt"
          (is= (w/getByte wrtr 1) (+ codes/INT_PACKED_3_ZERO (>>> ranges/BYTE_CHUNK_SIZE 16)) )
          ; writeRawInt16
          (is= (w/getByte wrtr 2) (bit-and (>>> ranges/BYTE_CHUNK_SIZE 8) 0xFF))
          (is= (w/getByte wrtr 3) (bit-and (>>> ranges/BYTE_CHUNK_SIZE 8) 0xFF)))
        (testing "writeRawBytes"
          (is= (w/getByte wrtr 4) 99)
          (is= (w/getByte wrtr (+ 3 ranges/BYTE_CHUNK_SIZE)) 99)))
      (testing "write remainder bytes"
        (is= (w/getByte wrtr (+ 4 ranges/BYTE_CHUNK_SIZE)) codes/BYTES)
        (is= (w/getByte wrtr (+ 5 ranges/BYTE_CHUNK_SIZE)) 2)
        (is= (w/getByte wrtr (+ 6 ranges/BYTE_CHUNK_SIZE)) 99)
        (is= (w/getByte wrtr (+ 7 ranges/BYTE_CHUNK_SIZE)) 99)))))

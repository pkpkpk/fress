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

(let [arr (js/Int8Array. 1)]
  (defn overflow [n]
    (aset arr 0 n)
    (aget arr 0)))

(deftest writeBytes-test
  (testing "(< length ranges/BYTES_PACKED_LENGTH_END)"
    (let [nums (range 0 6)
          bytes (js/Int8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (< (.-byteLength bytes) ranges/BYTES_PACKED_LENGTH_END))
      (w/writeBytes wrtr bytes)
      (is= -42 (w/getByte wrtr 0) (overflow (+ (.-byteLength bytes) codes/BYTES_PACKED_LENGTH_START)))
      (doseq [n nums]
        (is= n (w/getByte wrtr (inc n))))))
  (testing "ranges/BYTES_PACKED_LENGTH_END < length < ranges/BYTE_CHUNK_SIZE"
    (let [nums (range 0 15)
          bytes (js/Int8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (< ranges/BYTES_PACKED_LENGTH_END (alength bytes) ranges/BYTE_CHUNK_SIZE))
      (w/writeBytes wrtr bytes)
      (is= (w/getByte wrtr 0) (overflow codes/BYTES))
      (is= (w/getByte wrtr 1) (alength bytes))
      (doseq [n nums]
        (is= n (w/getByte wrtr (+ 2 n))))))
  (testing "ranges/BYTES_PACKED_LENGTH_END < ranges/BYTE_CHUNK_SIZE < length"
    (let [n 65537
          chunks (js/Math.floor (/ n ranges/BYTE_CHUNK_SIZE))
          remainder (mod n ranges/BYTE_CHUNK_SIZE)
          nums (take n (repeat 99))
          bytes (js/Int8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (< ranges/BYTES_PACKED_LENGTH_END ranges/BYTE_CHUNK_SIZE (alength bytes)))
      (is= chunks 1)
      (is= remainder 2)
      (w/writeBytes wrtr bytes)
      (testing "write chunk"
        (is= (w/getByte wrtr 0) (overflow codes/BYTES_CHUNK))
        (testing "writeCount -> writeInt"
          (is= (w/getByte wrtr 1) (overflow (+ codes/INT_PACKED_3_ZERO (>>> ranges/BYTE_CHUNK_SIZE 16))) )
          ; writeRawInt16
          (is= (w/getByte wrtr 2) (overflow (bit-and (>>> ranges/BYTE_CHUNK_SIZE 8) 0xFF)))
          (is= (w/getByte wrtr 3) (overflow (bit-and (>>> ranges/BYTE_CHUNK_SIZE 8) 0xFF))))
        (testing "writeRawBytes"
          (is= (w/getByte wrtr 4) 99)
          (is= (w/getByte wrtr (+ 3 ranges/BYTE_CHUNK_SIZE)) 99)))
      (testing "write remainder bytes"
        (is= (w/getByte wrtr (+ 4 ranges/BYTE_CHUNK_SIZE)) (overflow codes/BYTES))
        (is= (w/getByte wrtr (+ 5 ranges/BYTE_CHUNK_SIZE)) 2)
        (is= (w/getByte wrtr (+ 6 ranges/BYTE_CHUNK_SIZE)) 99)
        (is= (w/getByte wrtr (+ 7 ranges/BYTE_CHUNK_SIZE)) 99)))))
;
(deftest writeInt-test
  ;; hard coded nums taken from jvm
  ;; (fressian.api/byte-buffer-seq (fressian.api/byte-buf <OBJECT>))
  (let [wrt (w/Writer nil {})
        n 300]
    (w/writeInt wrt n)
    (is= 81 (w/getByte wrt 0) (+ codes/INT_PACKED_2_ZERO (>>> n 8)))
    (is= 44 (w/getByte wrt 1) ))
  (let [wrt (w/Writer nil {})
        n 3000]
    (w/writeInt wrt n)
    (is= 91 (w/getByte wrt 0) (+ codes/INT_PACKED_2_ZERO (>>> n 8)))
    (is= -72 (w/getByte wrt 1)))
  (let [wrt (w/Writer nil {})
        n -300]
    (w/writeInt wrt n)
    (is= 78 (w/getByte wrt 0))
    (is= -44 (w/getByte wrt 1))))

(def TextDecoder (js/TextDecoder.))
(def TextEncoder (js/TextEncoder.))

(defn getBuf [wrt] (.. wrt -raw-out -memory -buffer))

(deftest writeString-test
  (testing "small string, count fits in byte"
    (let [wrt (w/Writer nil {})
          s "hello world"
          bytes (.encode TextEncoder s)]
      (w/writeString wrt s)
      (is= (w/getByte wrt 0) (overflow codes/STRING))
      (is= (w/getByte wrt 1) (alength bytes))
      (let [tail (js/Uint8Array. (getBuf wrt) 2 (alength bytes))]
        (is= s (.decode TextDecoder tail)))))
  (testing "small string, count larger than byte"
    (let [wrt (w/Writer nil {})
          n 300
          s (.repeat "p" n)
          bytes (.encode TextEncoder s)]
      (w/writeString wrt s)
      (is= (w/getByte wrt 0) (overflow codes/STRING))
      (is= 81 (w/getByte wrt 1) (overflow (+ codes/INT_PACKED_2_ZERO (>>> n 8))))
      (is= 44 (w/getByte wrt 2) (overflow n))
      (let [tail (js/Uint8Array. (getBuf wrt) 3 (alength bytes))]
        (is= s (.decode TextDecoder tail))))))
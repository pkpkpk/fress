(ns fress.writer-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.raw-output :as rawOut]
            [fress.codes :as codes]
            [fress.ranges :as ranges]
            [fress.writer :as w]))

(defn is=
  ([a b] (is (= a b)))
  ([a b c] (is (= a b c)))
  ([a b c d] (is (= a b c d))))

(defn getByte [wrtr i]
  (rawOut/?getByte (.-raw-out wrtr) i))

(deftest writeBytes-test
  ; [(bytes.length + BYTES_PACKED_LENGTH_START ) , bytes[0], bytes[1],... ]
  (testing "(< length ranges/BYTES_PACKED_LENGTH_END)"
    (let [nums (range 0 7)
          bytes (js/Uint8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (< (alength bytes) ranges/BYTES_PACKED_LENGTH_END))
      (w/writeBytes wrtr bytes)
      (is= (getByte wrtr 0) (+ (alength bytes) codes/BYTES_PACKED_LENGTH_START))
      (doseq [n nums]
        (is= n (getByte wrtr (inc n))))))
  (testing "byte chunking"
    (let [nums (range 23 34)
          bytes (js/Uint8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (not (< (alength bytes) ranges/BYTES_PACKED_LENGTH_END)))
      (w/writeBytes wrtr bytes)

      )))
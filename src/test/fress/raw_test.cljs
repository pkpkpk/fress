(ns fress.raw-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.raw-output :as rawOut]
            [fress.impl.raw-input :as rawIn]))

;; need overflow behaviors
;; read/write oob
;; bad types
;; adler parity

(deftest rawBytes-test
  (testing "writeRawByte"
    (let [out (rawOut/raw-output)
          int (rawIn/raw-input (rawOut/getMemory out))]
      (is (zero? (rawOut/getBytesWritten out)))
      (is (zero? (aget (js/Int8Array. (.. out -memory -buffer)) 0)))
      (is (nil? (rawOut/getByte out 0)))
      (rawOut/writeRawByte out 127)
      (is (= 1 (rawOut/getBytesWritten out)))
      (is (= 127 (rawOut/getByte out 0)))
      (is (= 127 (aget (js/Int8Array. (.. out -memory -buffer)) 0)))))
  (testing "writeRawBytes"
    (let [out (rawOut/raw-output)
          bytes (js/Int8Array. #js [5 7 11 13 17])
          offset 0
          len 3]
      (is (zero? (rawOut/getBytesWritten out)))
      (is (zero? (aget (js/Int8Array. (.. out -memory -buffer)) 0)))
      (is (nil? (rawOut/getByte out 0)))
      (rawOut/writeRawBytes out bytes offset len)
      (is (= len (rawOut/getBytesWritten out)))
      (is (= 5 (rawOut/getByte out 0)))
      (is (= 7 (rawOut/getByte out 1)))
      (is (= 11 (rawOut/getByte out 2)))
      (is (nil? (rawOut/getByte out 3)))
      (let [offset 3
            len 2]
        (rawOut/writeRawBytes out bytes offset len)
        (is (= 11 (rawOut/getByte out 2)))
        (is (= 13 (rawOut/getByte out 3)))
        (is (= 17 (rawOut/getByte out 4)))
        (is (nil? (rawOut/getByte out 5))))))
  )
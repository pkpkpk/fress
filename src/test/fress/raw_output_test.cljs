(ns fress.raw-output-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.raw-output :as rawOut]))

;; need overflow behaviors
;; read/write oob
;; bad types
;; adler parity

(deftest rawBytes-test
  (testing "writeRawByte"
    (let [raw (rawOut/raw-output)]
      (is (zero? (rawOut/getBytesWritten raw)))
      (is (zero? (aget (js/Int8Array. (.. raw -memory -buffer)) 0)))
      (is (nil? (rawOut/getByte raw 0)))
      (rawOut/writeRawByte raw 127)
      (is (= 1 (rawOut/getBytesWritten raw)))
      (is (= 127 (rawOut/getByte raw 0)))
      (is (= 127 (aget (js/Int8Array. (.. raw -memory -buffer)) 0)))))
  (testing "writeRawBytes"
    (let [raw (rawOut/raw-output)
          bytes (js/Int8Array. #js [5 7 11 13 17])
          offset 0
          len 3]
      (is (zero? (rawOut/getBytesWritten raw)))
      (is (zero? (aget (js/Int8Array. (.. raw -memory -buffer)) 0)))
      (is (nil? (rawOut/getByte raw 0)))
      (rawOut/writeRawBytes raw bytes offset len)
      (is (= len (rawOut/getBytesWritten raw)))
      (is (= 5 (rawOut/getByte raw 0)))
      (is (= 7 (rawOut/getByte raw 1)))
      (is (= 11 (rawOut/getByte raw 2)))
      (is (nil? (rawOut/getByte raw 3)))
      (let [offset 3
            len 2]
        (rawOut/writeRawBytes raw bytes offset len)
        (is (= 11 (rawOut/getByte raw 2)))
        (is (= 13 (rawOut/getByte raw 3)))
        (is (= 17 (rawOut/getByte raw 4)))
        (is (nil? (rawOut/getByte raw 5)))))))
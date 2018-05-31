(ns fress.impl.raw-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.raw-input :as rawIn]))

(deftest rawBytes-test
  (testing "writeRawByte"
    (let [out (rawOut/raw-output)
          in (rawIn/raw-input (rawOut/getMemory out))]
      (is (zero? (rawOut/getBytesWritten out)))
      (is (zero? (aget (js/Int8Array. (.. out -memory -buffer)) 0)))
      (is (nil? (rawOut/getByte out 0)))
      (rawOut/writeRawByte out 127)
      (is (= 1 (rawOut/getBytesWritten out)))
      (is (= 127 (rawOut/getByte out 0)))
      (is (= 0 (rawIn/getBytesRead in)))
      (is (= 127 (rawIn/readRawByte in)))
      (is (= 1 (rawIn/getBytesRead in)))))
  (testing "writeRawBytes"
    (let [out (rawOut/raw-output)
          in (rawIn/raw-input (rawOut/getMemory out))
          bytes (js/Int8Array. #js [5 7 11 13 17])
          offset 0
          len 3]
      (is (zero? (rawOut/getBytesWritten out)))
      (is (zero? (aget (js/Int8Array. (.. out -memory -buffer)) 0)))
      (is (nil? (rawOut/getByte out 0)))
      (rawOut/writeRawBytes out bytes offset len)
      (is (= len (rawOut/getBytesWritten out)))
      (is (= 5 (rawOut/getByte out 0)))
      (is (= 5 (rawIn/readRawByte in)))
      (is (= 7 (rawOut/getByte out 1)))
      (is (= 7 (rawIn/readRawByte in)))
      (is (= 11 (rawOut/getByte out 2)))
      (is (= 11 (rawIn/readRawByte in)))
      (is (nil? (rawOut/getByte out 3)))
      ; (is (nil? (rawIn/readRawByte in))) need EOF solution for wasm
      (let [offset 3
            len 2]
        (rawOut/writeRawBytes out bytes offset len)
        (is (= 11 (rawOut/getByte out 2)))
        (is (= 13 (rawOut/getByte out 3)))
        (is (= 17 (rawOut/getByte out 4)))
        (is (nil? (rawOut/getByte out 5)))))))
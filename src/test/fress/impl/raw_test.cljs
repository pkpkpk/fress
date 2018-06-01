(ns fress.impl.raw-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.impl.buffer :as buf]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.raw-input :as rawIn]
            [fress.util :refer [byte-array log]]
            [fress.test-helpers :as helpers :refer [is=]]))

(deftest rawBytes-test
  (testing "writeRawByte/readRawByte"
    (let [memory (js/WebAssembly.Memory. #js{:initial 1})
          raw-out (rawOut/raw-output memory)
          raw-in  (rawIn/raw-input memory)]
      (is (zero? (rawIn/getBytesRead raw-in)))
      (is (zero? (rawOut/getBytesWritten raw-out)))
      (rawOut/writeRawByte raw-out 127)
      (is (= 1 (rawOut/getBytesWritten raw-out)))
      (is (= 127 (rawOut/getByte raw-out 0)))
      (is (= 0 (rawIn/getBytesRead raw-in)))
      (is (= 127 (rawIn/readRawByte raw-in)))
      (is (= 1 (rawIn/getBytesRead raw-in)))))
  (testing "writeRawBytes/readRawBytes"
    (let [memory (js/WebAssembly.Memory. #js{:initial 1})
          raw-out (rawOut/raw-output memory)
          raw-in  (rawIn/raw-input memory)
          bytes (byte-array [-2 -1 0 1 2])]
      (is (zero? (rawIn/getBytesRead raw-in)))
      (is (zero? (rawOut/getBytesWritten raw-out)))
      (rawOut/writeRawBytes raw-out bytes)
      (is (= (alength bytes) (rawOut/getBytesWritten raw-out)))
      (is= 254 (rawIn/readRawByte raw-in)) ;rolled
      (is= 255 (rawIn/readRawByte raw-in)) ;rolled
      (is= 0 (rawIn/readRawByte raw-in))
      (is= 1 (rawIn/readRawByte raw-in))
      (is= 2 (rawIn/readRawByte raw-in))
      (is (= (alength bytes) (rawIn/getBytesRead raw-in)))
      (rawIn/reset raw-in)
      (is (zero? (rawIn/getBytesRead raw-in)))
      (is= bytes (rawIn/readFully raw-in 5))
      (is (= (alength bytes) (rawIn/getBytesRead raw-in)))
      (let [offset 3
            len 2]
        (rawOut/reset raw-out)
        (rawIn/reset raw-in)
        (is (zero? (rawIn/getBytesRead raw-in)))
        (is (zero? (rawOut/getBytesWritten raw-out)))
        (rawOut/writeRawBytes raw-out bytes offset len)
        (is= 2 (rawOut/getBytesWritten raw-out))
        (is= 1 (rawIn/readRawByte raw-in))
        (is= 2 (rawIn/readRawByte raw-in))))))
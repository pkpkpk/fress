(ns fress.impl.raw-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.impl.buffer :as buf]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.raw-input :as rawIn]
            [fress.reader :as r]
            [fress.writer :as w]
            [fress.util :refer [byte-array log]]
            [fress.test-helpers :as helpers :refer [is= are-nums=]]))

(defn is-EOF? [r]
  (is
    (thrown-with-msg? js/Error #"EOF"
      (if (instance? rawIn/RawInput r)
        (rawIn/readRawByte r)
        (if (instance? r/FressianReader r)
          (r/readObject r)
          (throw (js/Error. "bad type given to is EOF")))))))

(deftest rawBytes-test
  (testing "writeRawByte/readRawByte"
    (let [memory (js/Uint8Array. 1)
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
    (let [bytes (byte-array [-2 -1 0 1 2])
          memory (js/Uint8Array. 5) ;(js/WebAssembly.Memory. #js{:initial 1})
          raw-out (rawOut/raw-output memory)
          raw-in  (rawIn/raw-input memory)]
      (is (zero? (rawIn/getBytesRead raw-in)))
      (is (zero? (rawOut/getBytesWritten raw-out)))
      (rawOut/writeRawBytes raw-out bytes)
      (is (= (alength bytes) (rawOut/getBytesWritten raw-out)))
      (is= 254 (rawIn/readRawByte raw-in)) ;rolled
      (is= 255 (rawIn/readRawByte raw-in)) ;rolled
      (is= 0 (rawIn/readRawByte raw-in))
      (is= 1 (rawIn/readRawByte raw-in))
      (is= 2 (rawIn/readRawByte raw-in))
      (is-EOF? raw-in)
      (is (= (alength bytes) (rawIn/getBytesRead raw-in)))
      (rawIn/reset raw-in)
      (is (zero? (rawIn/getBytesRead raw-in)))
      (are-nums= bytes (rawIn/readFully raw-in 5))
      (is (= (alength bytes) (rawIn/getBytesRead raw-in)))
      (testing "r/w multiple bytes + write with offset, length"
        (let [bytes (byte-array [39 40 41 42 43])
              offset 3
              len 2]
          (rawIn/reset raw-in)
          (rawOut/reset raw-out)
          (rawOut/writeRawBytes raw-out bytes)
          (are-nums= [39 40 41 42 43] (rawIn/readFully raw-in 5))
          (rawOut/reset raw-out)
          (rawIn/reset raw-in)
          (let [bytes (byte-array [75 76 77 78 79])]
            (is (zero? (rawIn/getBytesRead raw-in)))
            (is (zero? (rawOut/getBytesWritten raw-out)))
            (rawOut/writeRawBytes raw-out bytes offset len)
            (is= 2 (rawOut/getBytesWritten raw-out))
            (are-nums= [78 79] (rawIn/readFully raw-in 2)))))
      (testing "try to read too many"
        (rawIn/reset raw-in)
        (are-nums= [78 79 41 42 43] (rawIn/readFully raw-in (alength memory)))
        (rawIn/reset raw-in)
        (is (thrown? js/Error (rawIn/readFully raw-in (inc (alength memory)))))))))

(deftest write-stream-test
  (let [data [42 :foo 'bar {"baz" [8 9 10]}]]
    (testing "get readable bytes from buf/close(writestream)"
      (let [buffer (buf/write-stream)
            wrt (w/writer buffer)]
        (doseq [d data] (w/writeObject wrt d))
        (is  (.-open? buffer))
        (let [bytes (buf/close buffer)
              rdr (r/reader bytes)]
          (is (not (.-open? buffer)))
          (is= 42 (r/readObject rdr))
          (is= :foo (r/readObject rdr))
          (is= 'bar (r/readObject rdr))
          (is= {"baz" [8 9 10]} (r/readObject rdr))
          (is (thrown-with-msg? js/Error #"EOF" (r/readObject rdr))))))))

;; call reader on write-stream
;;  -not closed yet
;;  -already closed
;;
;; flush to external buffer?
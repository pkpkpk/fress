(ns fress.impl.raw-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.impl.buffer :as buf]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.raw-input :as rawIn]
            [fress.reader :as r]
            [fress.writer :as w]
            [fress.util :as util :refer [byte-array log]]
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
      (is (instance? rawOut/RawOutput raw-out))
      (is (implements? buf/IBufferWriter (.-out raw-out)))
      (is (instance? buf/BufferWriter (.-out raw-out)))
      (is (zero? (rawOut/getBytesWritten raw-out)))
      (rawOut/writeRawByte raw-out 127)
      (is (= 1 (rawOut/getBytesWritten raw-out)))
      (is (= 127 (rawOut/getByte raw-out 0)))
      (is (= 0 (rawIn/getBytesRead raw-in)))
      (is (= 127 (rawIn/readRawByte raw-in)))
      (is (= 1 (rawIn/getBytesRead raw-in)))))
  (testing "writeRawBytes/readRawBytes"
    (let [bytes (byte-array [-2 -1 0 1 2])
          memory (js/Uint8Array. 5)
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

(deftest byte-stream-test
  (let [data [42 :foo 'bar {"baz" [8 9 10]}]]
    (testing "get readable bytes from buf/close(writestream)"
      (let [buffer (buf/byte-stream)
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
          (is (thrown-with-msg? js/Error #"EOF" (r/readObject rdr))))))
    (testing "writeBytes"
      (let [in (util/i8-array [0 1 2 3 4 5 6 7 8 9])
            buffer (buf/byte-stream)]
        (buf/writeBytes buffer in)
        (is= (alength in) (buf/getBytesWritten buffer))
        (are-nums= (buf/toByteArray buffer) in)))
    (testing "writeBytes + offset"
      (let [in (util/i8-array [0 1 2 3 4 5 6 7 8 9])
            buffer (buf/byte-stream)]
        (buf/writeBytes buffer in 0 10)
        (is= 10 (buf/getBytesWritten buffer))
        (are-nums= (buf/toByteArray buffer) in))
      (let [in (util/i8-array [0 1 2 3 4 5 6 7 8 9])
            buffer (buf/byte-stream)]
        (buf/writeBytes buffer in 3 5)
        (is= 5 (buf/getBytesWritten buffer))
        (are-nums= (buf/toByteArray buffer) [3 4 5 6 7]))
      (let [in (util/i8-array [0 1 2 3 4 5 6 7 8 9])
            buffer (buf/byte-stream)]
        (buf/writeBytes buffer in 3 7)
        (is= 7 (buf/getBytesWritten buffer))
        (are-nums= (buf/toByteArray buffer) [3 4 5 6 7 8 9]))
      (testing "safe for excessive length"
        (let [in (util/i8-array [0 1 2 3 4 5 6 7 8 9])
              buffer (buf/byte-stream)]
          (buf/writeBytes buffer in 3 99)
          (is= 7 (buf/getBytesWritten buffer))
          (are-nums= (buf/toByteArray buffer) [3 4 5 6 7 8 9]))))
    (testing "flushTo"
      (let [out (util/i8-array [0 1 2 3 4 5 6 7 8 9])
            buffer (buf/byte-stream)]
        (buf/writeByte buffer 98)
        (buf/writeByte buffer 99)
        (buf/writeByte buffer 100)
        (is= 3 (buf/getBytesWritten buffer))
        (buf/flushTo buffer out 2)
        (are-nums= out [0 1 98 99 100 5 6 7 8 9])
        (testing "writeByte reset safety"
          (buf/reset buffer)
          (buf/writeByte buffer 101)
          (is= 1 (buf/getBytesWritten buffer))
          (buf/flushTo buffer out)
          (are-nums= out [101 1 98 99 100 5 6 7 8 9]))
        (testing "writeBytes reset safety"
          (let [out (util/i8-array [0 1 2 3 4 5 6 7 8 9])
                buffer (buf/byte-stream)]
            (buf/writeBytes buffer (byte-array [42 12 3 15 6 7]))
            (buf/reset buffer)
            (testing "flushTo no offset"
              (buf/writeBytes buffer (byte-array [99 100 101]))
              (buf/flushTo buffer out)
              (are-nums= out [99 100 101 3 4 5 6 7 8 9]))
            (testing "flushTo with offset"
              (buf/reset buffer)
              (buf/writeBytes buffer (byte-array [102 103]))
              (buf/flushTo buffer out 3)
              (are-nums= out [99 100 101 102 103 5 6 7 8 9]))
            (testing "oob dest should throw"
              (buf/reset buffer)
              (buf/writeBytes buffer (byte-array [99 100 101]) 0 3)
              (is (thrown? js/Error (buf/flushTo out 10) )))
            (testing "overfill  dest should throw"
              (buf/reset buffer)
              (buf/writeBytes buffer (byte-array [99 100 101]) 0 3)
              (is (thrown? js/Error (buf/flushTo out 8))))))))))



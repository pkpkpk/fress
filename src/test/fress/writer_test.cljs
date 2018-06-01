(ns fress.writer-test
  (:require-macros [fress.macros :refer [>>>]])
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.writer :as w]
            [fress.util :as util :refer [byte-array]]
            [fress.samples :as samples]
            [fress.test-helpers :as helpers :refer [log is= byteseq overflow]]))

(deftest integer-test
  (testing "write i16"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Short/MIN_VALUE", :value -32768 :bytes [103 -128 0] :rawbytes [103 128 0]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (is= out bytes)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (is= out bytes)))
  (testing "write i32"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Integer/MIN_VALUE", :value -2147483648, :bytes [117 -128 0 0 0], :rawbytes [117 128 0 0 0]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (is= out bytes)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (is= out bytes)))
  (testing "write i40"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(long -549755813887)", :value -549755813887, :bytes [121 -128 0 0 0 1], :rawbytes [121 128 0 0 0 1]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (is= out bytes)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (is= out bytes)))
  (testing "write i48"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(long 1.4073749E14)", :value 140737490000000, :bytes [126 -128 0 0 25 24 -128], :rawbytes [126 128 0 0 25 24 128]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (is= out bytes)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (is= out bytes)))
  (testing "write i64"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(long -9007199254740991)", :value -9007199254740991, :bytes [-8 -1 -32 0 0 0 0 0 1],  :rawbytes [248 255 224 0 0 0 0 0 1] :throw? false}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (is= out bytes)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (is= out bytes)))
  (testing "unsafe. this shouldn't work  but it does"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Long/MAX_VALUE", :value 9223372036854775807,  :bytes [-8 127 -1 -1 -1 -1 -1 -1 -1], :rawbytes [248 127 255 255 255 255 255 255 255] :throw? true}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (is= out bytes)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (is= out bytes)))
  (testing "int sampes"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/int-samples]
      (testing form
        (let [out (byte-array (count bytes))
              wrt (w/writer out)]
          (w/writeInt wrt value)
          (is= out bytes)
          (rawOut/reset (.-raw-out wrt))
          (w/writeObject wrt value)
          (is= out bytes))))))

#_(deftest writeBytes-test
  #_(testing "(< length ranges/BYTES_PACKED_LENGTH_END)"
    (let [nums (range 0 6)
          bytes (js/Int8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (< (.-byteLength bytes) ranges/BYTES_PACKED_LENGTH_END))
      (w/writeBytes wrtr bytes)
      (is= -42 (w/getByte wrtr 0) (overflow (+ (.-byteLength bytes) codes/BYTES_PACKED_LENGTH_START)))
      #_(doseq [n nums]
        (is= n (w/getByte wrtr (inc n))))))
  #_(testing "ranges/BYTES_PACKED_LENGTH_END < length < ranges/BYTE_CHUNK_SIZE"
    (let [nums (range 0 15)
          bytes (js/Int8Array. (into-array nums))
          wrtr (w/Writer nil {})]
      (is (< ranges/BYTES_PACKED_LENGTH_END (alength bytes) ranges/BYTE_CHUNK_SIZE))
      (w/writeBytes wrtr bytes)
      (is= (w/getByte wrtr 0) (overflow codes/BYTES))
      (is= (w/getByte wrtr 1) (alength bytes))
      (doseq [n nums]
        (is= n (w/getByte wrtr (+ 2 n))))))
  #_(testing "ranges/BYTES_PACKED_LENGTH_END < ranges/BYTE_CHUNK_SIZE < length"
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

(defn getBuf [wrt] (.. wrt -raw-out -memory -buffer))

#_(deftest writeString-test
  (testing "small string, count fits in byte"
    (let [wrt (w/Writer nil {})
          s "hello world"
          bytes (.encode util/TextEncoder s)]
      (w/writeString wrt s)
      (is= (w/getByte wrt 0) (overflow codes/STRING))
      (is= (w/getByte wrt 1) (alength bytes))
      (let [tail (js/Uint8Array. (getBuf wrt) 2 (alength bytes))]
        (is= s (.decode util/TextDecoder tail)))))
  (testing "small string, count larger than byte"
    (let [wrt (w/Writer nil {})
          n 300
          s (.repeat "p" n)
          bytes (.encode util/TextEncoder s)]
      (w/writeString wrt s)
      (is= (w/getByte wrt 0) (overflow codes/STRING))
      (is= 81 (w/getByte wrt 1) (overflow (+ codes/INT_PACKED_2_ZERO (>>> n 8))))
      (is= 44 (w/getByte wrt 2) (overflow n))
      (let [tail (js/Uint8Array. (getBuf wrt) 3 (alength bytes))]
        (is= s (.decode util/TextDecoder tail))))))

#_(deftest float-test
  (testing "writeFloat"
    (let [wrt (w/Writer nil {})
          f -99]
      (w/writeFloat wrt f)
      (is= -7 (w/getByte wrt 0) (overflow codes/FLOAT))
      (is= (byteseq wrt) [-7 -62 -58 0 0])))
  (testing "writeDouble"
    (let [wrt (w/Writer nil {})
          f -99]
      (w/writeDouble wrt f)
      (is= -6 (w/getByte wrt 0) (overflow codes/DOUBLE))
      (is= (byteseq wrt) [-6 -64 88 -64 0 0 0 0 0]))))

#_(deftest writeList-test
  (let [wrt (w/Writer nil {})
        lst []]
    (w/writeList wrt lst)
    (is= (byteseq wrt) [-28]))
  (let [wrt (w/Writer nil {})
        lst '(1 2 3)]
    (w/writeList wrt lst)
    (is= (w/getByte wrt 0) (overflow (+ (count lst) codes/LIST_PACKED_LENGTH_START)))
    (is= (byteseq wrt) [-25 1 2 3]))
  (let [wrt (w/Writer nil {})
        lst [true]]
    (w/writeList wrt lst)
    (is= (w/getByte wrt 0) (overflow (+ (count lst) codes/LIST_PACKED_LENGTH_START)))
    (is= (byteseq wrt) [-27 -11 ]))
  (let [wrt (w/Writer nil {})
        lst [nil]]
    (w/writeList wrt lst)
    (is= (w/getByte wrt 0) (overflow (+ (count lst) codes/LIST_PACKED_LENGTH_START)))
    (is= (byteseq wrt) [-27 -9 ]))
  (let [wrt (w/Writer nil {})
        lst [true nil]]
    (w/writeList wrt lst)
    (is= (w/getByte wrt 0) (overflow (+ (count lst) codes/LIST_PACKED_LENGTH_START)))
    (is= (byteseq wrt) [-26 -11 -9]))
  (let [wrt (w/Writer nil {})
        lst '("a")]
    (w/writeList wrt lst)
    (is= (w/getByte wrt 0) (overflow (+ (count lst) codes/LIST_PACKED_LENGTH_START)))
    (is= (byteseq wrt) [-27 -37 97]))
  (let [wrt (w/Writer nil {})
        lst ["hello" "world"]]
    (w/writeList wrt lst)
    (is= (w/getByte wrt 0) (overflow (+ (count lst) codes/LIST_PACKED_LENGTH_START)))
    (is= (byteseq wrt) '(-26 -33 104 101 108 108 111 -33 119 111 114 108 100))))

#_(deftest writeMap-test
  (testing "map count bug"
    (let [wrt (w/Writer)
          tag "map"
          code (codes/tag->code tag)
          m {"a" []}
          cnt (count (mapcat identity (seq m)))
          control '(-64 -26 -37 97 -28)]
      (w/writeObject wrt m)
      (is= -64 (w/getByte wrt 0) (overflow code))
      (is= -26 (w/getByte wrt 1) (overflow (+ cnt codes/LIST_PACKED_LENGTH_START)))
      (is= (byteseq wrt) control)))
  (testing "writeMap"
    (let [samples [[{} [-64 -28]]
                   [{"a" nil} '(-64 -26 -37 97 -9)]
                   [["a" []] '(-26 -37 97 -28)]
                   [{"a" []} '(-64 -26 -37 97 -28)]
                   [{"a" [1]} '(-64 -26 -37 97 -27 1)]
                   [{"a" [1 2 3]} '(-64 -26 -37 97 -25 1 2 3)]
                   [{"a" "b"} '(-64 -26 -37 97 -37 98)]
                   [{"a" #{}} '(-64 -26 -37 97 -63 -28)]]]
      (doseq [[m control] samples]
        (let [wrt (w/Writer)]
          (w/writeObject wrt m)
          (is= (byteseq wrt) control))))))


#_(deftest named-test
  (let [wrt (w/Writer)
        o :keyword]
    (w/writeObject wrt o)
    (is= (byteseq wrt) '(-54 -9 -31 107 101 121 119 111 114 100)))
  (let [wrt (w/Writer)
        o :named/keyword]
    (w/writeObject wrt o)
    (is= (byteseq wrt) '(-54 -33 110 97 109 101 100 -31 107 101 121 119 111 114 100))))


#_(deftest misc-types-test
  (testing "inst"
    (let [wrt (w/Writer)
          t 1527080360072
          date (js/Date. t)
          control '(-56 123 99 -115 21 24 -120)]
      (w/writeObject wrt date)
      (is= (byteseq wrt) control)))
  (testing "uri"
    (let [wrt (w/Writer)
          uri (goog.Uri. "https://clojurescript.org")
          control '(-59 -29 25 104 116 116 112 115 58 47 47 99 108 111 106 117 114 101 115 99 114 105 112 116 46 111 114 103)]
      (w/writeObject wrt uri)
      (is= (byteseq wrt) control)))
  (testing "regex"
    (let [wrt (w/Writer)
          re #"\n"
          control '(-60 -36 92 110)]
      (w/writeObject wrt re)
      (is= (byteseq wrt) control)))
  (testing "uuid"
    (let [wrt (w/Writer)
          u #uuid "0d6a32ef-1012-470b-92d2-45e25db8d09d"
          control '(-61 -39 16 13 106 50 -17 16 18 71 11 -110 -46 69 -30 93 -72 -48 -99)]
      (w/writeObject wrt u)
      (is= (byteseq wrt) control)))
  (testing "int[]"
    (let [wrt (w/Writer)
          a (js/Int32Array. #js[0 1 2 3 4 5])
          control '(-77 6 0 1 2 3 4 5)]
      (w/writeObject wrt a)
      (is= (byteseq wrt) control)))
  (testing "float[]"
    (let [wrt (w/Writer)
          a (js/Float32Array. #js[0 1 2 3 4 5])
          control '(-76 6 -7 0 0 0 0 -7 63 -128 0 0 -7 64 0 0 0 -7 64 64 0 0 -7 64 -128 0 0 -7 64 -96 0 0)]
      (w/writeObject wrt a)
      (is= (byteseq wrt) control)))
  (testing "double[]"
    (let [wrt (w/Writer)
          a (js/Float64Array. #js[0 1 2 3 4 5])
          control '(-79 6 -5 -4 -6 64 0 0 0 0 0 0 0 -6 64 8 0 0 0 0 0 0 -6 64 16 0 0 0 0 0 0 -6 64 20 0 0 0 0 0 0)]
      (w/writeObject wrt a)
      (is= (byteseq wrt) control)))
  (testing "boolean[]"
    (let [wrt (w/Writer)
          a [true false true false]
          control '(-78 4 -11 -10 -11 -10)]
      (w/writeAs wrt "boolean[]" a)
      (is= (byteseq wrt) control))))

(comment
 (testing "goog.math.Long"
   (let [wrt (w/Writer)
         l (goog.math.Long. 99)
         control '(80 99)]
     (w/writeObject wrt l)
     (is= (byteseq wrt) control))
   (let [wrt (w/Writer)
         l (goog.math.Long. 99999999999999999)
         control '(-8 1 99 69 120 93 -119 -1 -1)]
     (w/writeObject wrt l)
     (is= (byteseq wrt) control)))
 (testing "long[]"
   (let [wrt (w/Writer)
         a (mapv #(goog.math.Long. %) [0 1 2 3 4 5])
         control '(-80 6 0 1 2 3 4 5)]
     (w/writeObject wrt a)
     (is= (byteseq wrt) control))))



; all num types (see rawOutput)
; chars?, struct, caching, userHandlers + custom tags
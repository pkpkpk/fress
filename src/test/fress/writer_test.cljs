(ns fress.writer-test
  (:require-macros [fress.macros :refer [>>>]])
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.writer :as w]
            [fress.util :as util :refer [byte-array]]
            [fress.samples :as samples]
            [fress.test-helpers :as helpers :refer [log is= byteseq overflow are-bytes=]]))

#_(deftest integer-test
  (testing "write i16"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Short/MIN_VALUE", :value -32768 :bytes [103 -128 0] :rawbytes [103 128 0]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out)))
  (testing "write i32"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Integer/MIN_VALUE", :value -2147483648, :bytes [117 -128 0 0 0], :rawbytes [117 128 0 0 0]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out)))
  (testing "write i40"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(long -549755813887)", :value -549755813887, :bytes [121 -128 0 0 0 1], :rawbytes [121 128 0 0 0 1]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out)))
  (testing "write i48"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(long 1.4073749E14)", :value 140737490000000, :bytes [126 -128 0 0 25 24 -128], :rawbytes [126 128 0 0 25 24 128]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out)))
  (testing "write i64"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(long -9007199254740991)", :value -9007199254740991, :bytes [-8 -1 -32 0 0 0 0 0 1],  :rawbytes [248 255 224 0 0 0 0 0 1] :throw? false}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out)))
  (testing "unsafe."
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Long/MAX_VALUE", :value 9223372036854775807,  :bytes [-8 127 -1 -1 -1 -1 -1 -1 -1], :rawbytes [248 127 255 255 255 255 255 255 255] :throw? true}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (is (thrown? js/Error (w/writeInt wrt value)))
      (rawOut/reset (.-raw-out wrt))
      (is (thrown? js/Error (w/writeObject wrt value)))))
  (testing "int samples"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/int-samples]
      (testing form
        (let [out (byte-array (count bytes))
              wrt (w/writer out)]
          (if throw?
            (do
              (is (thrown? js/Error (w/writeInt wrt value)))
              (rawOut/reset (.-raw-out wrt))
              (is (thrown? js/Error (w/writeObject wrt value))))
            (do
              (w/writeInt wrt value)
              (are-bytes= bytes out)
              (rawOut/reset (.-raw-out wrt))
              (w/writeObject wrt value)
              (are-bytes= bytes out))))))))

#_(deftest floating-points-test
  (testing "writeFloat"
    (let [control-bytes [-7 -62 -58 0 0]
          out (byte-array (count control-bytes))
          wrt (w/writer out)
          f -99]
      (w/writeFloat wrt f)
      (is= -7 (w/getByte wrt 0) (overflow codes/FLOAT))
      (is= out control-bytes)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= control-bytes out)))
  (testing "writeDouble"
    (let [control-bytes [-6 -64 88 -64 0 0 0 0 0]
          out (byte-array (count control-bytes))
          wrt (w/writer out)
          f -99]
      (w/writeDouble wrt f)
      (is= -6 (w/getByte wrt 0) (overflow codes/DOUBLE))
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= control-bytes out)))
  (testing "floats"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/float-samples]
      (testing form
        (let [out (byte-array (count bytes))
              wrt (w/writer out)]
          (w/writeFloat wrt value)
          (are-bytes= bytes out)
          (rawOut/reset (.-raw-out wrt))
          (w/writeObject wrt value)
          (are-bytes= bytes out)))))
  (testing "doubles"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/double-samples]
      (testing form
        (let [out (byte-array (count bytes))
              wrt (w/writer out)]
          (w/writeDouble wrt value)
          (are-bytes= bytes out)
          (rawOut/reset (.-raw-out wrt))
          (w/writeObject wrt value)
          (are-bytes= bytes out))))))

#_(deftest writeBytes-test
  (testing "(< length ranges/BYTES_PACKED_LENGTH_END)"
    (let [{:keys [bytes input]} {:form "(byte-array [-2 -1 0 1 2])"
                                 :bytes [-43 -2 -1 0 1 2]
                                 :footer false
                                 :rawbytes [213 254 255 0 1 2]
                                 :input [-2 -1 0 1 2]}
          out (byte-array (count bytes))
          wrt (w/writer out)
          value (byte-array input)]
      (w/writeBytes wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out)))
  (testing "ranges/BYTES_PACKED_LENGTH_END < length < ranges/BYTE_CHUNK_SIZE"
    (let [{:keys [bytes input]} {:form "(byte-array (vec (range -6 7)))",
                                 :bytes [-39 13 -6 -5 -4 -3 -2 -1 0 1 2 3 4 5 6]
                                 :footer false
                                 :rawbytes [217 13 250 251 252 253 254 255 0 1 2 3 4 5 6]
                                 :input [-6 -5 -4 -3 -2 -1 0 1 2 3 4 5 6]}
          out (byte-array (count bytes))
          wrt (w/writer out)
          value (byte-array input)]
      (w/writeBytes wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out)))
  (testing "ranges/BYTES_PACKED_LENGTH_END < ranges/BYTE_CHUNK_SIZE < length"
    (let [{:keys [bytes input ]} @samples/chunked_bytes_sample
          out (byte-array (count bytes))
          wrt (w/writer out)
          value (byte-array (vec (take 70000 (repeat 99))))]
      (w/writeBytes wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out))))

#_(deftest writeString-test
  (testing "packed string, no chunks"
    (let [{:keys [bytes value]} {:form "\"hola\"", :bytes [-34 104 111 108 97], :footer false, :rawbytes [222 104 111 108 97], :value "hola"}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeString wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out)))
  (testing "string, no packing, no chunks"
    (let [{:keys [bytes value]} {:form "\"I'm a reasonable man, get off my case\"", :bytes [-29 37 73 39 109 32 97 32 114 101 97 115 111 110 97 98 108 101 32 109 97 110 44 32 103 101 116 32 111 102 102 32 109 121 32 99 97 115 101], :footer false, :rawbytes [227 37 73 39 109 32 97 32 114 101 97 115 111 110 97 98 108 101 32 109 97 110 44 32 103 101 116 32 111 102 102 32 109 121 32 99 97 115 101], :value "I'm a reasonable man, get off my case"}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeString wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out)))
  (testing "chunked string"
    (let [{:keys [bytes value]} @samples/chunked_string_sample
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeString wrt value)
      (are-bytes= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-bytes= bytes out))))

#_(deftest writeRawUTF8-test
  (doseq [{:keys [form bytes value tag? byte-count]} samples/utf8-samples]
    (testing form
      (let [out (byte-array (or byte-count (count bytes)))
            wrt (w/writer out)]
        (binding [w/*write-raw-utf8* true
                  w/*write-utf8-tag* tag?]
          (w/writeString wrt value))
        (are-bytes= bytes out)
        (rawOut/reset (.-raw-out wrt))
        (w/clearCaches wrt)
        (is (zero? (rawOut/getBytesWritten (.-raw-out wrt))))
        (binding [w/*write-raw-utf8* true
                  w/*write-utf8-tag* tag?]
          (w/writeObject wrt value))
        (are-bytes= bytes out)))))



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
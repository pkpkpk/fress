(ns fress.writer-test
  (:require-macros [fress.macros :refer [>>>]])
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [cljs.tools.reader :refer [read-string]]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.writer :as w]
            [fress.util :as util :refer [byte-array]]
            [fress.samples :as samples]
            [fress.test-helpers :as helpers :refer [log is= overflow are-nums=]]))

#_(deftest integer-test
  (testing "write i16"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Short/MIN_VALUE", :value -32768 :bytes [103 -128 0] :rawbytes [103 128 0]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out)))
  (testing "write i32"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Integer/MIN_VALUE", :value -2147483648, :bytes [117 -128 0 0 0], :rawbytes [117 128 0 0 0]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out)))
  (testing "write i40"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(long -549755813887)", :value -549755813887, :bytes [121 -128 0 0 0 1], :rawbytes [121 128 0 0 0 1]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out)))
  (testing "write i48"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(long 1.4073749E14)", :value 140737490000000, :bytes [126 -128 0 0 25 24 -128], :rawbytes [126 128 0 0 25 24 128]}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out)))
  (testing "write i64"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(long -9007199254740991)", :value -9007199254740991, :bytes [-8 -1 -32 0 0 0 0 0 1],  :rawbytes [248 255 224 0 0 0 0 0 1] :throw? false}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeInt wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out)))
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
              (are-nums= bytes out)
              (rawOut/reset (.-raw-out wrt))
              (w/writeObject wrt value)
              (are-nums= bytes out))))))))

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
      (are-nums= control-bytes out)))
  (testing "writeDouble"
    (let [control-bytes [-6 -64 88 -64 0 0 0 0 0]
          out (byte-array (count control-bytes))
          wrt (w/writer out)
          f -99]
      (w/writeDouble wrt f)
      (is= -6 (w/getByte wrt 0) (overflow codes/DOUBLE))
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= control-bytes out)))
  (testing "floats"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/float-samples]
      (testing form
        (let [out (byte-array (count bytes))
              wrt (w/writer out)]
          (w/writeFloat wrt value)
          (are-nums= bytes out)
          (rawOut/reset (.-raw-out wrt))
          (w/writeObject wrt value)
          (are-nums= bytes out)))))
  (testing "doubles"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/double-samples]
      (testing form
        (let [out (byte-array (count bytes))
              wrt (w/writer out)]
          (w/writeDouble wrt value)
          (are-nums= bytes out)
          (rawOut/reset (.-raw-out wrt))
          (w/writeObject wrt value)
          (are-nums= bytes out))))))

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
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out)))
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
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out)))
  (testing "ranges/BYTES_PACKED_LENGTH_END < ranges/BYTE_CHUNK_SIZE < length"
    (let [{:keys [bytes input ]} @samples/chunked_bytes_sample
          out (byte-array (count bytes))
          wrt (w/writer out)
          value (byte-array (vec (take 70000 (repeat 99))))]
      (w/writeBytes wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out))))

#_(deftest writeString-test
  (testing "packed string, no chunks"
    (let [{:keys [bytes value]} {:form "\"hola\"", :bytes [-34 104 111 108 97], :footer false, :rawbytes [222 104 111 108 97], :value "hola"}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeString wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out)))
  (testing "string, no packing, no chunks"
    (let [{:keys [bytes value]} {:form "\"I'm a reasonable man, get off my case\"", :bytes [-29 37 73 39 109 32 97 32 114 101 97 115 111 110 97 98 108 101 32 109 97 110 44 32 103 101 116 32 111 102 102 32 109 121 32 99 97 115 101], :footer false, :rawbytes [227 37 73 39 109 32 97 32 114 101 97 115 111 110 97 98 108 101 32 109 97 110 44 32 103 101 116 32 111 102 102 32 109 121 32 99 97 115 101], :value "I'm a reasonable man, get off my case"}
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeString wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out)))
  (testing "chunked string"
    (let [{:keys [bytes value]} @samples/chunked_string_sample
          out (byte-array (count bytes))
          wrt (w/writer out)]
      (w/writeString wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/writeObject wrt value)
      (are-nums= bytes out))))

#_(deftest writeRawUTF8-test
  (doseq [{:keys [form bytes value tag? byte-count]} samples/utf8-samples]
    (testing form
      (let [out (byte-array (or byte-count (count bytes)))
            wrt (w/writer out)]
        (binding [w/*write-raw-utf8* true
                  w/*write-utf8-tag* tag?]
          (w/writeString wrt value))
        (are-nums= bytes out)
        (rawOut/reset (.-raw-out wrt))
        (w/clearCaches wrt)
        (is (zero? (rawOut/getBytesWritten (.-raw-out wrt))))
        (binding [w/*write-raw-utf8* true
                  w/*write-utf8-tag* tag?]
          (w/writeObject wrt value))
        (are-nums= bytes out)))))

#_(deftest named-test
  (doseq [{:keys [form bytes value tag? byte-count]} samples/named-samples]
    (testing form
      (let [out (byte-array (or byte-count (count bytes)))
            wrt (w/writer out)]
        (w/writeObject wrt value)
        (are-nums= bytes out)))))

#_(deftest writeList-test
  (doseq [{:keys [form bytes value tag? byte-count]} samples/list-samples]
    (let [out (byte-array (or byte-count (count bytes)))
          wrt (w/writer out)]
      (w/writeList wrt value)
      (are-nums= bytes out)
      (rawOut/reset (.-raw-out wrt))
      (w/clearCaches wrt)
      (w/writeObject wrt value)
      (are-nums= bytes out))))

#_(deftest map-test
  (doseq [{:keys [form bytes value tag? byte-count]} samples/map-samples]
    (let [out (byte-array (or byte-count (count bytes)))
          wrt (w/writer out)]
      (w/writeObject wrt value)
      (are-nums= bytes out))))

(def typed-array-sym->writer
  {'byte-array   w/writeByteArray
   'int-array    w/writeIntArray
   'float-array  w/writeFloatArray
   'double-array w/writeDoubleArray
   'long-array   w/writeLongArray
   'object-array w/writeObjectArray
   'boolean-array w/writeBooleanArray})

#_(deftest typed-array-test
  (doseq [{:keys [form bytes value input byte-count]} samples/typed-array-samples]
    (testing form
      (let [out (byte-array (or byte-count (count bytes)))
            wrt (w/writer out)
            sym (first (read-string form))
            value ((helpers/typed-array-sym->fn sym) input)]
        ((typed-array-sym->writer sym) wrt value)
        (are-nums= bytes out)
        (when-let [t (helpers/type->typed-array-sym (type value))]
          (testing (str form " writeObject dispatch type->tag->code")
            (rawOut/reset (.-raw-out wrt))
            (w/writeObject wrt value)
            (are-nums= bytes out)))))))

#_(deftest misc-test
  (testing "inst"
    (doseq [{:keys [form bytes value input byte-count]} samples/inst-samples]
      (testing form
        (let [out (byte-array (or byte-count (count bytes)))
              wrt (w/writer out)]
          (w/writeInst wrt value)
          (are-nums= bytes out)
          (rawOut/reset (.-raw-out wrt))
          (w/writeObject wrt value)
          (are-nums= bytes out)))))
  (testing "uri"
    (doseq [{:keys [form bytes value input byte-count]} samples/uri-samples]
      (testing form
        (let [out (byte-array (or byte-count (count bytes)))
              wrt (w/writer out)
              value (goog.Uri. input)]
          (w/writeUri wrt value)
          (are-nums= bytes out)
          (rawOut/reset (.-raw-out wrt))
          (w/writeObject wrt value)
          (are-nums= bytes out)))))
  (testing "uuid"
    (doseq [{:keys [form bytes value input byte-count]} samples/uuid-samples]
      (testing form
        (let [out (byte-array (or byte-count (count bytes)))
              wrt (w/writer out)]
          (w/writeUUID wrt value)
          (are-nums= bytes out)
          (rawOut/reset (.-raw-out wrt))
          (w/writeObject wrt value)
          (are-nums= bytes out)))))
  (testing "regex"
    (doseq [{:keys [form bytes value input byte-count]} samples/regex-samples]
      (testing form
        (let [out (byte-array (or byte-count (count bytes)))
              wrt (w/writer out)]
          (w/writeRegex wrt value)
          (are-nums= bytes out)
          (rawOut/reset (.-raw-out wrt))
          (w/writeObject wrt value)
          (are-nums= bytes out)))))
  (testing "sets"
    (doseq [{:keys [form bytes value input byte-count]} samples/set-samples]
      (testing form
        (let [out (byte-array (or byte-count (count bytes)))
              wrt (w/writer out)]
          (w/writeSet wrt value)
          (are-nums= bytes out)
          (rawOut/reset (.-raw-out wrt))
          (w/clearCaches wrt)
          (w/writeObject wrt value)
          (are-nums= bytes out))))))

#_(deftest writeFooter-test
  (doseq [{:keys [form bytes input value byte-count]} samples/footer-samples]
    (let [out (byte-array (or byte-count (count bytes)))
          wrt (w/writer out)
          value (or value (byte-array input))]
      (w/writeObject wrt value)
      (w/writeFooter wrt)
      (are-nums= bytes out))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype Person [firstName lastName]
  IEquiv
  (-equiv [this that]
    (and (= (type this) (type that))
         (= firstName (.-firstName that))
         (= lastName (.-lastName that)))))

(defn writePerson
  [wrt person]
  (w/writeTag wrt "org.fressian.Examples.Person" 2)
  (w/writeObject wrt (.-firstName person))
  (w/writeObject wrt (.-lastName person)))

#_(deftest write-person-test
  (let [bytes [-17 -29 28 111 114 103 46 102 114 101 115 115 105 97 110 46 69 120 97 109 112 108 101 115 46 80 101 114 115 111 110 2 -33 106 111 110 110 121 -29 9 103 114 101 101 110 119 111 111 100 -96 -34 116 104 111 109 -33 121 111 114 107 101]
        tag "org.fressian.Examples.Person"]
    (testing "write person with write-handler"
      (let [out (byte-array (count bytes))
            wrt (w/writer out :handlers {Person writePerson})]
        (w/writeObject wrt (->Person "jonny" "greenwood")) ;<= triggers struct caching
        (w/writeObject wrt (->Person "thom" "yorke"))
        (are-nums= bytes out)))))

#_(deftest cached-test
  (let [{:keys [value bytes]} samples/cached-sample
        out (byte-array (count bytes))
        wrt (w/writer out)]
    (w/writeObject wrt value true)
    (w/writeObject wrt value true)
    (are-nums= bytes out)))

(defrecord Book [author title])

(deftest write-record-test
  (let [{:keys [bytes author title class-sym]} samples/record-sample
        out (byte-array (count bytes))
        wrt (w/writer out)
        value (Book. author title)]
    (binding [w/*record->name* {Book "fress.api.Book"}]
      (w/writeObject wrt value)
      (are-nums= bytes out))))
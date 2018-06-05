(ns fress.reader-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [cljs.tools.reader :refer [read-string]]
            [fress.impl.raw-input :as rawIn]
            [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.reader :as r]
            [fress.samples :as samples]
            [fress.util :refer [byte-array] :as util]
            [fress.test-helpers :as helpers :refer
             [log is= byteseq rawbyteseq are-nums= overflow precision= float=]]))

#_(deftest readInt-test
  (testing "readRawInt40"
    (let [{:keys [form bytes value rawbytes]} {:form "(long -549755813887)"
                                               :value -549755813887
                                               :bytes [121 -128 0 0 0 1]
                                               :rawbytes [121 128 0 0 0 1]}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (is= 121 (rawIn/readRawByte raw))
      (let [i40 (rawIn/readRawInt40 raw)]
        (is= 549755813889 i40)
        (is= 549755813889 (.toNumber (goog.math.Long.fromNumber i40))))
      (rawIn/reset (:raw-in rdr))
      (is= value (r/readInt rdr))
      (rawIn/reset (:raw-in rdr))
      (is= value (r/readObject rdr))))
  (testing "readRawInt48"
    (let [{:keys [form bytes value rawbytes]} {:form "(long 1.4073749E14)"
                                               :value 140737490000000
                                               :bytes [126 -128 0 0 25 24 -128]
                                               :rawbytes [126 128 0 0 25 24 128]}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (is= 126 (rawIn/readRawByte raw))
      (let [i48 (rawIn/readRawInt48 raw)]
        (is= i48 140737490000000  (.toNumber (goog.math.Long.fromNumber i48))))
      (rawIn/reset (:raw-in rdr))
      (is= value (r/readInt rdr))
      (rawIn/reset (:raw-in rdr))
      (is= value (r/readObject rdr))))
  (testing "readRawInt64"
    (let [{:keys [form bytes value rawbytes]} {:form "(long 9007199254740991)"
                                               :value 9007199254740991
                                               :bytes [-8 0 31 -1 -1 -1 -1 -1 -1]
                                               :rawbytes [248 0 31 255 255 255 255 255 255]}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset (:raw-in rdr))
      (testing "by component"
        (is= 248 (r/readNextCode rdr) codes/INT)
        (let [i64 (rawIn/readRawInt64 raw)]
          (is= i64 9007199254740991 (.toNumber (goog.math.Long.fromNumber i64)))))
      (rawIn/reset (:raw-in rdr))
      (is= value (r/readInt rdr))
      (rawIn/reset (:raw-in rdr))
      (is= value (r/readObject rdr))))
  (testing "unsafe i64"
    (let [{:keys [form bytes value rawbytes]}{:form "(long 9007199254740992)"
                                              :value 9007199254740992
                                              :bytes [-8 0 32 0 0 0 0 0 0]
                                              :rawbytes [248 0 32 0 0 0 0 0 0]}
          rdr (r/reader (byte-array bytes))]
      (is= 248 (rawIn/readRawByte (:raw-in rdr)))
      (binding [rawIn/*throw-on-unsafe?* true]
        (is (thrown? js/Error (rawIn/readRawInt64 (:raw-in rdr)))))))
  (testing "int-samples"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/int-samples]
      (testing form
        (let [rdr (r/reader (byte-array bytes))]
          (are-nums= rawbytes (rawbyteseq rdr))
          (rawIn/reset (:raw-in rdr))
          (if throw?
            (is (thrown? js/Error (r/readInt rdr)))
            (do
              (is= value (r/readInt rdr))
              (rawIn/reset (:raw-in rdr))
              (is= value (r/readObject rdr)))))))))

#_(deftest read-floats-test
  (testing "Float/MAX_VALUE"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Float/MAX_VALUE",
                                                      :value 3.4028235E38,
                                                      :bytes [-7 127 127 -1 -1],
                                                      :rawbytes [249 127 127 255 255]}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= 249 (rawIn/readRawByte raw))
      (is (precision= value (rawIn/readRawFloat raw) 8))
      (rawIn/reset raw)
      (is (precision= value (r/readFloat rdr) 8))
      (rawIn/reset raw)
      (is (float= value (r/readObject rdr)))))
  (testing "readFloat"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/float-samples]
      (testing form
        (let [rdr (r/reader (byte-array bytes))
              raw (:raw-in rdr)]
          (are-nums= rawbytes (rawbyteseq rdr))
          (rawIn/reset raw)
          (is (float= value (r/readFloat rdr)))
          (rawIn/reset raw)
          (is (float= value (r/readObject rdr))))))))

#_(deftest read-double-test
  (testing "Double/MAX_VALUE"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Double/MAX_VALUE",
                                                      :value 1.7976931348623157E308
                                                      :bytes [-6 127 -17 -1 -1 -1 -1 -1 -1]
                                                      :rawbytes [250 127 239 255 255 255 255 255 255]}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is (precision= value (r/readDouble rdr) 16))
      (rawIn/reset raw)
      (is (float= value (r/readObject rdr)))))
  (testing "double-samples"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/double-samples]
      (testing form
        (let [rdr (r/reader (byte-array bytes))
              raw (:raw-in rdr)]
          (are-nums= rawbytes (rawbyteseq rdr))
          (rawIn/reset raw)
          (is (float= value (r/readDouble rdr))))))))

#_(deftest bytes-test
  (testing "packed bytes"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(byte-array [-1 -2 -3 0 1 2 3])"
                                                      :bytes [-41 -1 -2 -3 0 1 2 3]
                                                      :rawbytes [215 255 254 253 0 1 2 3]}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)
          input (second (read-string form))]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) (+ (count input) codes/BYTES_PACKED_LENGTH_START))
      (rawIn/reset raw)
      (are-nums= (byte-array input) (r/readObject rdr))))
  (testing "not packed, no chunks"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(byte-array [-4 -3 -2 -1 0 1 2 3 4])",
                                                      :bytes [-39 9 -4 -3 -2 -1 0 1 2 3 4],
                                                      :rawbytes [217 9 252 253 254 255 0 1 2 3 4]}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)
          input (second (read-string form))]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) codes/BYTES)
      (is= (r/readNextCode rdr) (count input))
      (rawIn/reset raw)
      (are-nums= (byte-array input) (r/readObject rdr))))
  (testing "chunked"
    (let [{:keys [form bytes value rawbytes throw?]} @samples/chunked_bytes_sample
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)
          input (vec (take 70000 (repeat 99)))]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) codes/BYTES_CHUNK)
      (is= (r/readCount- rdr) ranges/BYTE_CHUNK_SIZE)
      (is= (rawIn/readRawByte (:raw-in rdr)) 99)
      (rawIn/reset raw)
      (are-nums= (byte-array input) (r/readObject rdr)))))

#_(deftest string-test
  (testing "packed string"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "\"hola\"",
                                                      :bytes [-34 104 111 108 97],
                                                      :rawbytes [222 104 111 108 97],
                                                      :value "hola"}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) (+ codes/STRING_PACKED_LENGTH_START (count value)))
      (is= (rawIn/readRawByte raw) (.charCodeAt "h" 0))
      (is= (rawIn/readRawByte raw) (.charCodeAt "o" 0))
      (is= (rawIn/readRawByte raw) (.charCodeAt "l" 0))
      (is= (rawIn/readRawByte raw) (.charCodeAt "a" 0))
      (rawIn/reset raw)
      (is= value (r/readObject rdr))))
  (testing "no packing, no chunking"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(apply str (take 20 (repeat \\A)))", :bytes [-29 20 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65], :rawbytes [227 20 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65], :value "AAAAAAAAAAAAAAAAAAAA"}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) codes/STRING)
      (is= (r/readCount- rdr) (count value))
      (rawIn/reset raw)
      (is= value (r/readObject rdr))))
  (testing "chunked"
    (let [{:keys [form bytes value rawbytes throw?]} @samples/chunked_string_sample
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) codes/STRING_CHUNK)
      (is= (r/readCount- rdr) (inc util/U16_MAX_VALUE))
      (is= (rawIn/readRawByte (:raw-in rdr)) (.charCodeAt "A" 0))
      (rawIn/reset raw)
      (is= value (r/readObject rdr))))
  (testing "emoji"
    (let [{:keys [form bytes value rawbytes throw?]}{:form "\"😉 😎 🤔 😐 🙄\"",
                                                     :bytes [-29 34 -19 -96 -67 -19 -72 -119 32 -19 -96 -67 -19 -72 -114 32 -19 -96 -66 -19 -76 -108 32 -19 -96 -67 -19 -72 -112 32 -19 -96 -67 -19 -71 -124]
                                                     :rawbytes [227 34 237 160 189 237 184 137 32 237 160 189 237 184 142 32 237 160 190 237 180 148 32 237 160 189 237 184 144 32 237 160 189 237 185 132]
                                                     :value "😉 😎 🤔 😐 🙄"}
          rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= 227 (r/readNextCode rdr) codes/STRING)
      (rawIn/reset raw)
      (is= value (r/readObject rdr)))))

#_(deftest utf8-type-test
  (doseq [{:keys [form bytes value rawbytes throw?]} samples/utf8-samples]
    (let [rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= value (r/readObject rdr)))))

(def regular-string-sample
  {:form "\"one ring to rule them all 😉 😎 🤔 😐 🙄 \"",
   :bytes [-29 61 111 110 101 32 114 105 110 103 32 116 111 32 114 117 108 101 32 116 104 101 109 32 97 108 108 32 -19 -96 -67 -19 -72 -119 32 -19 -96 -67 -19 -72 -114 32 -19 -96 -66 -19 -76 -108 32 -19 -96 -67 -19 -72 -112 32 -19 -96 -67 -19 -71 -124 32]
   :rawbytes [227 61 111 110 101 32 114 105 110 103 32 116 111 32 114 117 108 101 32 116 104 101 109 32 97 108 108 32 237 160 189 237 184 137 32 237 160 189 237 184 142 32 237 160 190 237 180 148 32 237 160 189 237 184 144 32 237 160 189 237 185 132 32]
   :value "one ring to rule them all 😉 😎 🤔 😐 🙄 "})

(def utf8+code-sample
  {:form "(->utf8 \"one ring to rule them all 😉 😎 🤔 😐 🙄 \")",
   :bytes [-65 51 111 110 101 32 114 105 110 103 32 116 111 32 114 117 108 101 32 116 104 101 109 32 97 108 108 32 -16 -97 -104 -119 32 -16 -97 -104 -114 32 -16 -97 -92 -108 32 -16 -97 -104 -112 32 -16 -97 -103 -124 32]
   :rawbytes [191 51 111 110 101 32 114 105 110 103 32 116 111 32 114 117 108 101 32 116 104 101 109 32 97 108 108 32 240 159 152 137 32 240 159 152 142 32 240 159 164 148 32 240 159 152 144 32 240 159 153 132 32]
   :value "one ring to rule them all 😉 😎 🤔 😐 🙄 "})

(defn utf8-benchmark [sample n]
  (let [{:keys [form bytes value rawbytes throw?]} sample]
    (let [rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)]
      (when (do
              (assert (= rawbytes (rawbyteseq rdr)))
              (rawIn/reset raw)
              (assert (= value (r/readObject rdr)))
              (rawIn/reset raw)
              true)
        (simple-benchmark [] (do (r/readObject rdr) (rawIn/reset raw)) n)))))

(def n 10000)
(defn regular-string-benchmark [] (utf8-benchmark regular-string-sample n))
(defn utf8+code-benchmark [] (utf8-benchmark utf8+code-sample n))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_(deftest named-test
  (doseq [{:keys [form bytes value tag? byte-count]} samples/named-samples]
    (testing form
      (let [out (byte-array bytes)
            rdr (r/reader out)]
        (is= value (r/readObject rdr))))))

#_(deftest map-test
  (doseq [{:keys [form bytes value tag? byte-count]} samples/map-samples]
    (testing form
      (let [out (byte-array bytes)
            rdr (r/reader out)]
        (is= value (r/readObject rdr))))))

#_(deftest typed-array-test
  (doseq [{:keys [form bytes input rawbytes throw?]} samples/typed-array-samples]
    (let [rdr (r/reader (byte-array bytes))
          raw (:raw-in rdr)
          value ((helpers/typed-array-sym->fn (first (read-string form))) input)]
      (are-nums= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (let [o (r/readObject rdr)]
        (is= (type value) (type o))
        (when-let [t (helpers/type->typed-array-sym (type value))]
          (are-nums=  value o))))))

#_(deftest misc-test
  (testing "inst"
    (doseq [{:keys [form bytes value input byte-count]} samples/inst-samples]
      (testing form
        (let [out (byte-array bytes)
              rdr (r/reader out)]
          (is= value (r/readObject rdr))))))
  (testing "uri"
    (doseq [{:keys [form bytes value input byte-count]} samples/uri-samples]
      (testing form
        (let [out (byte-array bytes)
              rdr (r/reader out)
              value (goog.Uri. input)]
          (is= (.toString  value) (.toString (r/readObject rdr)))))))
  (testing "uuid"
    (doseq [{:keys [form bytes value input byte-count]} samples/uuid-samples]
      (testing form
        (let [out (byte-array bytes)
              rdr (r/reader out)]
          (is= value (r/readObject rdr))))))
  (testing "regex"
    (doseq [{:keys [form bytes value input byte-count]} samples/regex-samples]
      (testing form
        (let [out (byte-array bytes)
              rdr (r/reader out)]
          (is= value (r/readObject rdr))))))
  (testing "sets"
    (doseq [{:keys [form bytes value input byte-count]} samples/set-samples]
      (testing form
        (let [out (byte-array bytes)
              rdr (r/reader out)]
          (is= value (r/readObject rdr)))))))

#_(deftest footer-test
  (doseq [{:keys [form bytes input rawbytes throw? footer value]} samples/footer-samples]
    (testing form
      (let [rdr (r/reader (byte-array bytes) :validateAdler? true)
            raw (:raw-in rdr)
            value (or value ((sym->fn (first (read-string form))) input))]
        (are-nums= rawbytes (rawbyteseq rdr))
        (rawIn/reset raw)
        (if input
          (are-nums= value (r/readObject rdr))
          (is= value (r/readObject rdr)))
        (is (nil? (r/validateFooter rdr)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;struct

(deftype Person [firstName lastName]
  IEquiv
  (-equiv [this that]
    (and (= (type this) (type that))
         (= firstName (.-firstName that))
         (= lastName (.-lastName that)))))

(defn readPerson [rdr tag fields]
  (Person. (r/readObject rdr) (r/readObject rdr)))

#_(deftest read-person-test
  (let [bytes [-17 -29 28 111 114 103 46 102 114 101 115 115 105 97 110 46 69 120 97 109 112 108 101 115 46 80 101 114 115 111 110 2 -33 106 111 110 110 121 -29 9 103 114 101 101 110 119 111 111 100 -96 -34 116 104 111 109 -33 121 111 114 107 101]
        tag "org.fressian.Examples.Person"]
    (testing "reading without handler"
      (let [rdr (r/reader (byte-array bytes))
            raw (:raw-in rdr)]
        (testing "by component..."
          (is= (r/readNextCode rdr) codes/STRUCTTYPE)
          (is= (r/readObject rdr) tag)
          (is= (r/readObject rdr) 2)
          (is= (r/readObject rdr) "jonny")
          (is= (r/readObject rdr) "greenwood")
          ;; writer has now cached the object..., reader keeps record
          ;; at cache index 0
          (is= (r/readNextCode rdr) (+ codes/STRUCT_CACHE_PACKED_START 0))
          (is= (r/readObject rdr) "thom")
          (is= (r/readObject rdr) "yorke"))
        (rawIn/reset raw)
        (r/resetCaches rdr)
        (testing "readObject no handler => TaggedObject"
          (let [o (r/readObject rdr)]
            (is (instance? r/TaggedObject o))
            (is= (.-tag o) tag)
            (is= (vec (.-value o)) ["jonny" "greenwood"]))
          (let [o (r/readObject rdr)]
            (is (instance? r/TaggedObject o))
            (is= (.-tag o) tag)
            (is= (vec (.-value o)) ["thom" "yorke"])))))
    (testing "with read handler"
      (let [rdr (r/reader (byte-array bytes) :handlers {tag readPerson})
            raw (:raw-in rdr)]
        (is= (r/readObject rdr) (Person. "jonny" "greenwood"))
        (is= (r/readObject rdr) (Person. "thom" "yorke"))))))

#_(deftest read-cached-test
  (let [bytes [-51 -63 -23 -33 104 101 108 108 111 1 79 -42 -10 -56 123 99 -79 -33 -94 -3 -128]
        value #{"hello" 1 -42 false #inst "2018-05-30T16:26:53.565-00:00"}
        rdr (r/reader (byte-array bytes))
        raw (:raw-in rdr)]
    (testing "by component"
      (is= (r/readNextCode rdr) codes/PUT_PRIORITY_CACHE)
      (is= (r/readObject rdr) value)
      (is= (r/readNextCode rdr) (+ codes/PRIORITY_CACHE_PACKED_START 0))
      (is (thrown-with-msg? js/Error #"EOF" (r/readObject rdr))))
    (rawIn/reset raw)
    (r/resetCaches rdr)
    (testing "normal use"
      (is= (r/readObject rdr) value)
      (is= (r/readObject rdr) value))))
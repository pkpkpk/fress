(ns fress.reader-test
  (:require-macros [fress.macros :refer [>>>]]
                   [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :as casync
             :refer [close! put! take! alts! <! >! chan promise-chan timeout]]
            [cljs.test :refer-macros [deftest is testing async]]
            [cljs-node-io.core :refer [slurp]]
            [cljs.tools.reader :refer [read-string]]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.raw-input :as rawIn]
            [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.reader :as r]
            [fress.util :refer [byte-array] :as util]
            [fress.test-helpers :as helpers
             :refer [log jvm-byteseq is= byteseq overflow into-bytes ;<= byte-array in util
                     precision= kinda=]]
            [fress.samples :as samples]))

(defn rawbyteseq [rdr]
  (let [raw (.-raw-in rdr)
        acc #js[]]
    (loop []
      (let [byte (rawIn/readRawByte raw)]
        (if-not byte
          (vec acc)
          (do
            (.push acc byte)
            (recur)))))))

(deftest readInt-test
  (testing "readRawInt40"
    (let [{:keys [form bytes value rawbytes]} {:form "(long -549755813887)"
                                               :value -549755813887
                                               :bytes [121 -128 0 0 0 1]
                                               :rawbytes [121 128 0 0 0 1]}
          rdr (r/reader (into-bytes bytes))
           raw (:raw-in rdr)]
      (is= 121 (rawIn/readRawByte raw))
      (let [i40 (rawIn/readRawInt40 raw)]
        (is= 549755813889 i40)
        (is= 549755813889 (.toNumber (goog.math.Long.fromNumber i40))))
      (rawIn/reset (:raw-in rdr))
      (is= value (r/readInt rdr))))
  (testing "readRawInt48"
    (let [{:keys [form bytes value rawbytes]} {:form "(long 1.4073749E14)"
                                               :value 140737490000000
                                               :bytes [126 -128 0 0 25 24 -128]
                                               :rawbytes [126 128 0 0 25 24 128]}
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (is= 126 (rawIn/readRawByte raw))
      (let [i48 (rawIn/readRawInt48 raw)]
        (is= i48 140737490000000  (.toNumber (goog.math.Long.fromNumber i48))))
      (rawIn/reset (:raw-in rdr))
      (is= value (r/readInt rdr))))
  (testing "readRawInt64"
    (let [{:keys [form bytes value rawbytes]} {:form "(long 9007199254740991)"
                                               :value 9007199254740991
                                               :bytes [-8 0 31 -1 -1 -1 -1 -1 -1]
                                               :rawbytes [248 0 31 255 255 255 255 255 255]}
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (when rawbytes
        (is= rawbytes (rawbyteseq rdr))
        (rawIn/reset (:raw-in rdr)))
      (is= 248 (rawIn/readRawByte raw))
      (let [i64 (rawIn/readRawInt64 raw)]
        (is= i64 9007199254740991 (.toNumber (goog.math.Long.fromNumber i64))))
      (rawIn/reset (:raw-in rdr))
      (is= value (r/readInt rdr))))
  (testing "unsafe i64"
    (let [{:keys [form bytes value rawbytes]}{:form "(long 9007199254740992)"
                                              :value 9007199254740992
                                              :bytes [-8 0 32 0 0 0 0 0 0]
                                              :rawbytes [248 0 32 0 0 0 0 0 0]}
          rdr (r/reader (into-bytes bytes))]
      (is= 248 (rawIn/readRawByte (:raw-in rdr)))
      (binding [rawIn/*throw-on-unsafe?* true]
        (is (thrown? js/Error (rawIn/readRawInt64 (:raw-in rdr)))))))
  (testing "int-samples"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/int-samples]
      (testing form
        (let [rdr (r/reader (into-bytes bytes))]
          (is= rawbytes (rawbyteseq rdr))
          (rawIn/reset (:raw-in rdr))
          (if throw?
            (is (thrown? js/Error (r/readInt rdr)))
            (is= value (r/readInt rdr))))))))

#_(deftest read-floats-test
  (testing "Float/MAX_VALUE"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Float/MAX_VALUE",
                                                      :value 3.4028235E38,
                                                      :bytes [-7 127 127 -1 -1],
                                                      :rawbytes [249 127 127 255 255]}
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (is= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= 249 (rawIn/readRawByte raw))
      (is (precision= value (rawIn/readRawFloat raw) 8))
      (rawIn/reset raw)
      (is (precision= value (r/readFloat rdr) 8))))
  (testing "readFloat"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/float-samples]
      (testing form
        (let [rdr (r/reader (into-bytes bytes))
              raw (:raw-in rdr)]
          (is= rawbytes (rawbyteseq rdr))
          (rawIn/reset raw)
          (is (kinda= value (r/readFloat rdr))))))))

#_(deftest read-double-test
  (testing "Double/MAX_VALUE"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "Double/MAX_VALUE",
                                                      :value 1.7976931348623157E308
                                                      :bytes [-6 127 -17 -1 -1 -1 -1 -1 -1]
                                                      :rawbytes [250 127 239 255 255 255 255 255 255]}
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (is= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is (precision= value (r/readDouble rdr) 16))))
  (testing "double-samples"
    (doseq [{:keys [form bytes value rawbytes throw?]} samples/double-samples]
      (testing form
        (let [rdr (r/reader (into-bytes bytes))
              raw (:raw-in rdr)]
          (is= rawbytes (rawbyteseq rdr))
          (rawIn/reset raw)
          (is (kinda= value (r/readDouble rdr))))))))

#_(deftest bytes-test
  (testing "packed bytes"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(byte-array [-1 -2 -3 0 1 2 3])"
                                                      :bytes [-41 -1 -2 -3 0 1 2 3]
                                                      :rawbytes [215 255 254 253 0 1 2 3]}
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)
          input (second (read-string form))]
      (is= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) (+ (count input) codes/BYTES_PACKED_LENGTH_START))
      (rawIn/reset raw)
      (is= (byte-array input) (r/readObject rdr))))
  (testing "not packed, no chunks"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "(byte-array [-4 -3 -2 -1 0 1 2 3 4])",
                                                      :bytes [-39 9 -4 -3 -2 -1 0 1 2 3 4],
                                                      :rawbytes [217 9 252 253 254 255 0 1 2 3 4]}
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)
          input (second (read-string form))]
      (is= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) codes/BYTES)
      (is= (r/readNextCode rdr) (count input))
      (rawIn/reset raw)
      (is= (byte-array input) (r/readObject rdr))))
  (testing "chunked"
    (let [{:keys [form bytes value rawbytes throw?]} samples/chunked_bytes_sample
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)
          input (vec (take 70000 (repeat 99)))]
      (is= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) codes/BYTES_CHUNK)
      (is= (r/readCount- rdr) ranges/BYTE_CHUNK_SIZE)
      (is= (rawIn/readRawByte (:raw-in rdr)) 99)
      (rawIn/reset raw)
      (is= (byte-array input) (r/readObject rdr)))))

#_(deftest string-test
  (testing "packed string"
    (let [{:keys [form bytes value rawbytes throw?]} {:form "\"hola\"",
                                                      :bytes [-34 104 111 108 97],
                                                      :rawbytes [222 104 111 108 97],
                                                      :value "hola"}
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (is= rawbytes (rawbyteseq rdr))
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
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (is= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= (r/readNextCode rdr) codes/STRING)
      (is= (r/readCount- rdr) (count value))
      (rawIn/reset raw)
      (is= value (r/readObject rdr))))
  (testing "chunked"
    (let [{:keys [form bytes value rawbytes throw?]} samples/chunked_string_sample
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (is= rawbytes (rawbyteseq rdr))
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
          rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (is= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= 227 (r/readNextCode rdr) codes/STRING)
      (rawIn/reset raw)
      (is= value (r/readObject rdr)))))

#_(deftest utf8-type-test
  (doseq [{:keys [form bytes value rawbytes throw?]} samples/utf8-samples]
    (let [rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (is= rawbytes (rawbyteseq rdr))
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
    (let [rdr (r/reader (into-bytes bytes))
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
#_(deftest inst-test
  (doseq [{:keys [form bytes value rawbytes throw?]} samples/inst-samples]
    (let [rdr (r/reader (into-bytes bytes))
          raw (:raw-in rdr)]
      (is= rawbytes (rawbyteseq rdr))
      (rawIn/reset raw)
      (is= value (r/readObject rdr)))))

; (deftest misc-types
;   (doseq [{:keys [form bytes value rawbytes throw?]} samples/misc-samples]
;     (let [rdr (r/reader (into-bytes bytes))
;           raw (:raw-in rdr)]
;       (is= rawbytes (rawbyteseq rdr))
;       (rawIn/reset raw)
;       (is= (read-string form) (r/readObject rdr)))))


; uuid
;;int[] , long [], float[], double[], boolean[]
; list, openlist, closedlist
; structs
; footers, caching,, EOF
; unknown tag => TaggedObject
;; bad regex, bad uri, bad uuid


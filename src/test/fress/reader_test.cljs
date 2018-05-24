(ns fress.reader-test
  (:require-macros [fress.macros :refer [>>>]]
                   [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :as casync
             :refer [close! put! take! alts! <! >! chan promise-chan timeout]]
            [cljs.test :refer-macros [deftest is testing async]]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.raw-input :as rawIn]
            [fress.codes :as codes]
            [fress.ranges :as ranges]
            [fress.reader :as r]
            [fress.test-helpers :as helpers
             :refer [log jvm-byteseq is= byteseq overflow into-bytes]]))

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



(def int-samples
  [{:form "(short 55)", :value 55, :bytes [55], :rawbytes [55]}
   {:form "(short -55)", :value -55, :bytes [79 -55], :rawbytes [79 201]}
   {:form "(short -32700)", :value -32700, :bytes [103 -128 68], :rawbytes [103 128 68]}
   {:form "(short 32700)", :value 32700, :bytes [104 127 -68], :rawbytes [104 127 188]}
   ; min i16
   {:form "(short -32768)", :value -32768, :bytes [103 -128 0], :rawbytes [103 128 0]}
   ; max i16
   {:form "(short 32767)", :value 32767, :bytes [104 127 -1], :rawbytes [104 127 255]}
   ;min i32
   {:form "(int -2147483648)", :value -2147483648, :bytes [117 -128 0 0 0], :rawbytes [117 128 0 0 0]}
   ;max i32
   {:form "(int  2147483647)", :value 2147483647, :bytes [118 127 -1 -1 -1], :rawbytes [118 127 255 255 255]}
   ;;;;min int40
   {:form "(long -549755813887)", :value -549755813887, :bytes [121 -128 0 0 0 1], :rawbytes [121 128 0 0 0 1]}
   ;;; max int40
   {:form "(long 549755813888)", :value 549755813888, :bytes [122 -128 0 0 0 0], :rawbytes [122 128 0 0 0 0]}
   ;;;; max int48
   {:form "(long 1.4073749E14)", :value 140737490000000, :bytes [126 -128 0 0 25 24 -128], :rawbytes [126 128 0 0 25 24 128]}
   ;MAX_SAFE_INT
   {:form "(long 9007199254740991)", :value 9007199254740991, :bytes [-8 0 31 -1 -1 -1 -1 -1 -1], :rawbytes [248 0 31 255 255 255 255 255 255]}
   ;;MIN_SAFE_INTEGER
   {:form "(long -9007199254740991)", :value -9007199254740991, :bytes [-8 -1 -32 0 0 0 0 0 1], :rawbytes [248 255 224 0 0 0 0 0 1]}])

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
    (doseq [{:keys [form bytes value rawbytes]} int-samples]
      (testing form
        (let [rdr (r/reader (into-bytes bytes))]
          (when rawbytes
            (is= rawbytes (rawbyteseq rdr))
            (rawIn/reset (:raw-in rdr)))
          (is= value (r/readInt rdr)))))))


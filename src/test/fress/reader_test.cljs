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
  [
   {:form "(short 55)", :value 55, :bytes [55], :rawbytes [55]}
   {:form "(short -55)", :value -55, :bytes [79 -55], :rawbytes [79 201]}
   {:form "(short -32700)", :value -32700, :bytes [103 -128 68], :rawbytes [103 128 68]}
   {:form "(short 32700)", :value 32700, :bytes [104 127 -68], :rawbytes [104 127 188]}
   {:form "(int (- 2147483000))", :value -2147483000, :bytes [117 -128 0 2 -120], :rawbytes [117 128 0 2 136]}
   {:form "(int 2147483000)", :value 2147483000, :bytes [118 127 -1 -3 120], :rawbytes [118 127 255 253 120]}


   ])

(deftest readInt-test
  (doseq [{:keys [form bytes value rawbytes]} int-samples]
    (testing form
      (let [rdr (r/reader (into-bytes bytes))]
        (when rawbytes
          (is= rawbytes (rawbyteseq rdr))
          (rawIn/reset (:raw-in rdr)))
        (is= value (r/readInt rdr)))))
  )

; (= -4294967296 (bit-shift-left (- 117 118) 32))

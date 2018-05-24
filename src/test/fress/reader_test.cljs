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
   ["(short 55)" [55] 55]
   ["(short -55)" [79 -55] -55]
   ["(short 32700)" [104 127 -68] 32700]
   ["(short -32700)" [103 128 68] -32700]
   ["(int (- 2147483000))" [117 -128 0 2 -120] -2147483000 [117 128 0 2 136]]

   ])



(deftest readInt-test
  ; (let [[desc control-bytes control-val control-raw-bytes]
  ;       ["(int (- 2147483000))" [117 -128 0 2 -120] -2147483000 [117 128 0 2 136]]
  ;       rdr (r/reader (into-bytes control-bytes))]
  ;   (is= control-raw-bytes (rawbyteseq rdr))
  ;   (rawIn/reset (:raw-in rdr))
  ;   (= 117 (r/readNextCode rdr))
  ;   (= 2147484296 (rawIn/readRawInt32 (:raw-in rdr)))
  ;   (rawIn/reset (:raw-in rdr))
  ;   (is= control-val (r/readInt rdr))
  ;   )

  (doseq [[desc control-bytes control-val raw-bytes] int-samples]
    (testing desc
      (let [rdr (r/reader (into-bytes control-bytes))]
        (when raw-bytes
          (is= raw-bytes (rawbyteseq rdr))
          (rawIn/reset (:raw-in rdr)))
        (is= control-val (r/readInt rdr)))))
  )

; (= -4294967296 (bit-shift-left (- 117 118) 32))

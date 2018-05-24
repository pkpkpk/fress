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

(def int-samples
  [
   ["(short 55)" [55] 55]
   ["(short 32700)" [104 127 -68] 32700]
   ["(short -32700)" [103 128 68] -32700]
   ])

(deftest readInt-test
  (doseq [[desc control-bytes control-val] int-samples]
    (testing desc
      (let [rdr (r/reader (into-bytes control-bytes))]
        (is= control-val (r/readInt rdr))))))
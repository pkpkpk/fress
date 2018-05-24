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

(deftest readInt-test
  (testing "short"
    (let [[control-val control-bytes] [55 [55]]
          in (into-bytes control-bytes)
          rdr (r/reader in)]
      (is= control-val (r/readInt rdr)))
    ; (let [[control-val control-bytes] [32700 [104 127 -68]]
    ;       in (into-bytes control-bytes)
    ;       rdr (r/reader in)]
    ;   (is= control-val (r/readInt rdr)))
    )

  )
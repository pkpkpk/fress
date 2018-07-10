(ns fress.runner
  (:require [cljs.test :refer-macros [run-tests]]
            [cljs.nodejs :as nodejs]
            [fress.roundtrip-test]
            [fress.impl.hopmap-test]
            [fress.impl.raw-test]))

(nodejs/enable-util-print!)

(defn -main []
  (run-tests
   'fress.roundtrip-test
   'fress.impl.hopmap-test
   'fress.impl.raw-test))

(set! *main-cli-fn* -main)
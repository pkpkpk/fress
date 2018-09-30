(ns fress.wasm-runner
  (:require [cljs.test :refer-macros [run-tests]]
            [cljs.nodejs :as nodejs]
            [fress.wasm-test]))

(nodejs/enable-util-print!)

(defn -main []
  (run-tests
   'fress.wasm-test))

(set! *main-cli-fn* -main)
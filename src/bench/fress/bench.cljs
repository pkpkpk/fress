(ns fress.bench
  (:require-macros [fress.bench :refer [benchmark] :as bench])
  (:require [cognitect.transit :as transit]
            [cljs.nodejs :as nodejs]
            [fress.api :as fress]
            [fress.impl.buffer :as buf]
            [goog.math]))

(nodejs/enable-util-print!)

; node > 8.5
; https://nodejs.org/api/perf_hooks.html#perf_hooks_class_performanceobserver

(def now
  (if (exists? js/performance)
    (js/performance.now.bind js/performance)
    (js/Date.now.bind js/Date)))

;;==============================================================================
;; transit for comparison

(defn assert-transit-rt!
  [data]
  (let [writer (transit/writer :json)
        reader (transit/reader :json)]
    (assert (= data (transit/read reader (transit/write writer data))))))

(defn transit-bench
  [trials iterations payload]
  (assert-transit-rt! payload)
  (benchmark []
    (let [writer (transit/writer :json)
          reader (transit/reader :json)]
      (transit/read reader (transit/write writer payload)))
    trials iterations))

;;==============================================================================
;; fress bench

(defn assert-reset-control! [payload]
  (let [bs (fress/byte-stream)]
    (dotimes [_ 10]
      (do
        (fress/write-object (fress/create-writer bs) payload)
        (fress/read-object (fress/create-reader bs))
        (buf/reset bs)))
    (assert (zero? (alength @bs)))))

(defn fress-bench
  [trials iterations payload]
  (assert-reset-control! payload)
  (binding [
            ; fress.writer/*write-raw-utf8* true
            ]
    (benchmark [bs (fress/byte-stream)]
      (do
        (fress/write-object (fress/create-writer bs) payload)
        (fress/read-object (fress/create-reader bs))
        (buf/reset bs))
      trials iterations)))

;;==============================================================================
(def data [{::sym 'foo/bar
            'inst (js/Date.)
            :set #{42 true false nil "some unicode 😉 😎 🤔 😐 🙄"}}])


(defn run-bench
  ([]
   (run-bench 25 100 data))
  ([trials iterations payload]
   (let []
     {:transit-summary (transit-bench trials iterations payload)
      :fress-summary (fress-bench trials iterations payload)
      :arch (.-arch js/process)
      :platform (.-platform js/process)
      :node-version (.-version js/process)})))

(defn -main []
  (println (run-bench)))

(set! *main-cli-fn* -main)

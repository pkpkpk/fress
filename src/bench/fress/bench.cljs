(ns fress.bench
  (:require-macros [fress.bench :refer [benchmark] :as bench])
  (:require [cognitect.transit :as transit]
            [cljs.nodejs :as nodejs]
            [fress.api :as fress]
            [fress.impl.buffer :as buf]))

(nodejs/enable-util-print!)

; node > 8.5
; https://nodejs.org/api/perf_hooks.html#perf_hooks_class_performanceobserver

(def now
  (if (exists? js/performance)
    (js/performance.now.bind js/performance)
    (js/Date.now.bind js/Date)))

(def data [{::sym 'foo/bar
            :kw  ::kw
            :inst (js/Date.now)
            :set #{42 true nil "string string string string string"}}])

(defn assert-transit-rt! [data]
  (let [writer (transit/writer :json)
        reader (transit/reader :json)]
    (assert (= data (transit/read reader (transit/write writer data))))))

(defn transit-rt-bench* [data n]
  (assert-transit-rt! data)
  (benchmark []
    (let [writer (transit/writer :json)
          reader (transit/reader :json)]
      (transit/read reader (transit/write writer data)))
    n))

(defn transit-write-bench* [data n]
  (assert-transit-rt! data)
  (benchmark []
    (let [writer (transit/writer :json)]
      (transit/write writer data))
    n))

(defn transit-bench [] (transit-rt-bench* data 100))
(defn transit-write-bench [] (transit-write-bench* data 100))



(defn fress-rt-bench* [data n]
  (assert (= data (fress/read (fress/write data))))
  (binding [fress.writer/*write-raw-utf8* true]
    (benchmark []
      (let [bs (fress/byte-stream)]
        (fress/write-object (fress/create-writer bs) data)
        (fress/read-object (fress/create-reader bs)))
      n)))

(defn assert-reset-control! []
  (let [bs (fress/byte-stream)]
    (dotimes [_ 10]
      (do
        (fress/write-object (fress/create-writer bs) data)
        (fress/read-object (fress/create-reader bs))
        (buf/reset bs)))
    (assert (zero? (alength @bs)))))

(defn fress-rt-reset [data n]
  (assert-reset-control!)
  (binding [fress.writer/*write-raw-utf8* true]
    (benchmark [bs (fress/byte-stream)]
               (do
                 (fress/write-object (fress/create-writer bs) data)
                 (fress/read-object (fress/create-reader bs))
                 (buf/reset bs))
               n)))

(defn fress-write-bench* [data n]
  (assert (= data (fress/read (fress/write data))))
  (binding [fress.writer/*write-raw-utf8* true]
    (benchmark [] (fress/write data) n)))


(defn fress-bench [] (fress-rt-bench* data 100))

(defn fress-reset-bench [] (fress-rt-reset data 100))

(defn fress-write-bench [] (fress-write-bench* data 100))

(defn -main []
  (println
   {:transit-summary (transit-bench)
    :fress-summary (fress-bench)
    :fress-reset-summary (fress-reset-bench)
    :arch (.-arch js/process)
    :platform (.-platform js/process)
    :node-version (.-version js/process)}))

(set! *main-cli-fn* -main)

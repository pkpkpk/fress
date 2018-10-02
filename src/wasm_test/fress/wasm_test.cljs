(ns fress.wasm-test
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [fress.test-macros :as tmac])
  (:require [cljs.core.async :as casync :refer [close! put! take! alts! <! >! chan promise-chan]]
            [cljs.test :refer-macros [deftest is testing async are run-tests] :as test]
            [cargo.api :as cargo]
            [fress.api :as api]
            [fress.wasm :as wasm-api]
            [fress.util :as util :refer [expected byte-array log]]))

(def path (js/require "path"))

(extend-type goog.Uri
  IEquiv
  (-equiv [this that] (= (.toString this) (.toString that))))

(extend-type js/RegExp
  IEquiv
  (-equiv [this that] (= (.toString this) (.toString that))))

(extend-type js/Int32Array
  IEquiv
  (-equiv [this that] (= (array-seq this) (array-seq that))))

(extend-type js/Float32Array
  IEquiv
  (-equiv [this that] (= (array-seq this) (array-seq that))))

(extend-type js/Float64Array
  IEquiv
  (-equiv [this that] (= (array-seq this) (array-seq that))))

(def cfg
  {:project-name "wasm-test"
   :dir (path.join (tmac/root) "resources" "wasm-test")
   :target :wasm
   :verbose true
   :release? true
   :rustflags {:allow []}})

(defonce module (atom nil))

(defn p->ch
  "convert promise to nodeback style [?err ?data] yielding promise-chan"
  ([promise](p->ch promise (promise-chan)))
  ([promise c]
   (let []
      (.then promise
        (fn [value] (put! c [nil value]))
        (fn [reason](put! c [reason])))
     c)))

(defn build []
  (js/console.clear)
  (take! (cargo/build-wasm cfg)
    (fn [[err {:keys [buffer]}]]
      (if err
        (cargo/report-error err)
        (take! (p->ch (wasm-api/instantiate buffer))
          (fn [[err Mod :as init-res]]
            (if err
              (js/console.error err)
              (do
                (assert (implements? wasm-api/IFressWasmModule Mod))
                (reset! module Mod)))))))))


(defn hello []
  (if-let [Mod @module]
    (wasm-api/call Mod "hello")
    (throw (js/Error "missing module"))))

(defn big-string []
  (if-let [Mod @module]
    (wasm-api/call Mod "big_string")
    (throw (js/Error "missing module"))))

(defn get-custom-error []
  (if-let [Mod @module]
    (wasm-api/call Mod "get_custom_error")
    (throw (js/Error "missing module"))))

(defn echo [any]
   (if-let [Mod @module]
     (binding [fress.reader/*keywordize-keys* false
               fress.writer/*stringify-keys* false]
      (wasm-api/call Mod "echo" any)) ;<-------- (wasm-api/write Mod any)
     (throw (js/Error "missing module"))))

(defn get-errors ;=> [?err ?[[err0 err1 err2 err3]]]
  ([]
   (if-let [Mod @module]
     (binding [fress.reader/*keywordize-keys* true]
       (wasm-api/call Mod "get_errors"))
     (throw (js/Error "missing module")))))

(defn induce-panic [] ;=> [{:type :panic, :msg "..."}]
  (if-let [Mod @module]
    (wasm-api/call Mod "induce_panic")
    (throw (js/Error "missing module"))))

(defn bad-int []
  (if-let [Mod @module]
    (wasm-api/call Mod "bad_int")
    (throw (js/Error "missing module"))))

(defn get-raw-err []
  (if-let [Mod @module]
    (wasm-api/call Mod "get_raw_err")
    (throw (js/Error "missing module"))))

(defn get-res-err []
  (if-let [Mod @module]
    (wasm-api/call Mod "get_res_err")
    (throw (js/Error "missing module"))))

(defn panic-test []
  (let [[err ok :as res] (induce-panic)]
    (is (= :panic (get err :type)))))

(defn serialization-error-test []
  (testing "when serializing fails, it's derived error is picked up as [err]"
    (is (= (bad-int) [{"position" 0,
                       "ErrorCode" "IntTooLargeFori64",
                       "type" "serde-fressian",
                       "category" "Ser"}]))))

(defn err-test []
  (binding [fress.reader/*keywordize-keys* true]
    (testing "naked errors serialize to [nil ok]"
      (is (= (get-raw-err) [nil
                            {:type "serde-fressian"
                             :category "Misc"
                             :ErrorCode "Message"
                             :value "some message"
                             :position 0}])))
    (testing "Err(Error) serializes to [err]"
      (is (= (get-res-err) [{:type "serde-fressian"
                             :category "Misc"
                             :ErrorCode "Message"
                             :value "some message"
                             :position 0}])))))

(defn write-bytes-test []
  (let [bytes (util/u8-array [99 100 101])
        fptr (wasm-api/write-bytes @module bytes)
        length (.-len fptr)
        ptr (.-ptr fptr)
        view (js/Uint8Array. (.. @module -exports -memory -buffer))]
    (is (== 99 (aget view ptr)))
    (is (== 100 (aget view (+ ptr 1))))
    (is (== 101 (aget view (+ ptr 2))))
    (wasm-api/dealloc @module ptr length)))

(defn errors-test []
  (let [[_ errors] (get-errors)]
    (is (= (nth errors 0) {:type "serde-fressian"
                           :category "Misc"
                           :ErrorCode "Message"
                           :value "some message"
                           :position 0}))
    (is (= (nth errors 1) {:type "serde-fressian"
                           :category "De"
                           :ErrorCode "UnmatchedCode"
                           :value 42
                           :position 43}))
    (is (= (nth errors 2) {:type "serde-fressian"
                           :category "Ser"
                           :ErrorCode "UnsupportedCacheType"
                           :position 99}))))

(defn custom-error-test []
  (let [[err] (get-custom-error)]
    (is (= err {"type" "test_lib_error"
                "field_0" "some message"}))))

(def ts
  [nil
   true
   false
   0
   -1
   js/Number.MIN_SAFE_INTEGER
   js/Number.MAX_SAFE_INTEGER
   0.0
   1.0
   ;;; 99.999
   1e2
   1.0e2
   1.1e2
   ;;; 1.23e-4
   js/Number.MIN_VALUE
   js/Number.MAX_VALUE
   (js/Int8Array.  #js[-2 -1 0 1 2])
   ; (js/Uint8Array. #js[-2 -1 0 1 2])
   ""
   "hello"
   "cellar door"
   "😉 😎 🤔 😐 🙄😉 😎 🤔 😐 🙄"
   :foo
   ::foo
   'foo
   'foo/bar
   'foo.bar/baz
   []
   [true false true]
   [true true true]
   [0 1 2]
   [-4 -3 -2 -1 0 1 2 3 4]
   [:foo :foo :foo :foo]
   [::foo ::foo ::foo ::foo]
   {:foo :bar}
   {:foo {::foo {":bar" "baz"}}}
   {:foo 0 ::bar [0 1 2]}
   {"string" 0}
   [true -99 nil "😉 😎 🤔 😐 🙄😉 😎 🤔 😐 🙄" (js/Int8Array. #js[-2 -1 0 1 2])]
   #{:foo "bar" [1 2 3]}
   #inst "2018-09-14T08:48:33.569-00:00"
   #"\n"
   (goog.Uri. "https://www.youtube.com/watch?v=PJfeoo5GW-w")
   #uuid "e2a1e404-5d43-40f2-8cbf-a4820bfe0f27"
   (js/Int32Array. #js[1 2 3])
   (js/Float32Array. #js[1 2 3])
   (js/Float64Array. #js[1 2 3])
   ])

(defn echo-test []
  (let [f (fn [any] (is (= (echo any) [nil any])))]
    (run! f ts)))




; BIGINT
; BIGDEC
; LONG_ARRAY
; BOOLEAN_ARRAY
; OBJECT_ARRAY
; struct
; (defrecord SomeRec [f0])
; TaggedObject


(defn mod-tests []
  (write-bytes-test)
  (panic-test)
  (serialization-error-test)
  (err-test)
  (errors-test)
  (custom-error-test)
  (echo-test)
  )

(deftest wasm-test
  (async done
    (take! (cargo/build-wasm cfg)
      (fn [[err {:keys [buffer]}]]
        (if (is (nil? err))
          (take! (p->ch (wasm-api/instantiate buffer))
            (fn [[err Mod :as init-res]]
              (if (is (some? Mod))
                (do
                  (reset! module Mod)
                  (mod-tests)
                  (done))
                (done))))
          (do
            (cargo/report-error err)
            (done)))))))
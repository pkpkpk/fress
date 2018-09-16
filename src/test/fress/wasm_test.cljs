(ns fress.wasm-test
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [fress.test-macros :as tmac])
  (:require [cljs.core.async :as casync :refer [close! put! take! alts! <! >! chan promise-chan]]
            [cljs.test :refer-macros [deftest is testing async are run-tests] :as test]
            [cargo.cargo :as cargo]
            [fress.api :as api]
            [fress.wasm :as wasm-api]
            [fress.util :as util :refer [expected byte-array log]]))

(def path (js/require "path"))

(def cfg
  {:project-name "wasm-test"
   :dir (path.join (tmac/root) "resources" "wasm-test")
   :target :wasm
   :release? true
   :rustflags {:allow [
                       :non_snake_case
                       :unused_parens
                       :unused_variables

                       :unused_imports
                       :dead_code

                       :non_camel_case_types
                       ]}})

(defonce module (atom nil))

(defn build []
  (take! (cargo/build-wasm! cfg)
    (fn [[e Mod]]
      (if-not e
        (reset! module Mod)))))

(declare mod-tests)

(deftest wasm-test
  (async done
    (set! cargo/*verbose* false)
    (take! (cargo/build! cfg)
      (fn [[e buffer]]
        (if-not (is (and (nil? e) (instance? js/Buffer buffer)))
          (done)
          (take! (cargo/init-module buffer #js{})
            (fn [[e compiled]]
              (if-not (is (and (nil? e) (instance? js/WebAssembly.Instance (.. compiled -instance))))
                (done)
                (do
                  (mod-tests (reset! module (.. compiled -instance)))
                  (set! cargo/*verbose* true)
                  (done))))))))))

(defn hello []
  (if-let [Mod @module]
    (let [read-ptr ((.. Mod -exports -hello))]
      (wasm-api/read Mod read-ptr))
    (throw (js/Error "missing module"))))

(defn big-string []
  (if-let [Mod @module]
    (let [read-ptr ((.. Mod -exports -big_string))]
      (wasm-api/read Mod read-ptr))
    (throw (js/Error "missing module"))))

(defn get-custom-error []
  (if-let [Mod @module]
    (let [read-ptr ((.. Mod -exports -get_custom_error))]
      (wasm-api/read Mod read-ptr))
    (throw (js/Error "missing module"))))

(defn echo
  ([](echo "hello from javascript"))
  ([any]
   (if-let [Mod @module]
     (binding [fress.reader/*keywordize-keys* false
               fress.writer/*stringify-keys* false]
       (let [[write-ptr length :as foo] (wasm-api/write Mod any)
             read-ptr ((.. Mod -exports -echo) write-ptr length)]
         (wasm-api/read Mod read-ptr)))
     (throw (js/Error "missing module")))))

(defn get-errors ;=> [?err ?[[err0 err1 err2 err3]]]
  ([]
   (if-let [Mod @module]
     (let [read-ptr ((.. Mod -exports -get_errors))]
       (binding [fress.reader/*keywordize-keys* true]
         (wasm-api/read Mod read-ptr)))
     (throw (js/Error "missing module")))))

(defn errors-test []
  (let [[_ errors] (get-errors)]
    (is (= (nth errors 0) {:type "serde-fressian"
                           :category "misc"
                           :ErrorCode "Message"
                           :value "some message"
                           :position 0}))
    (is (= (nth errors 1) {:type "serde-fressian"
                           :category "de"
                           :ErrorCode "UnmatchedCode"
                           :value 42
                           :position 43}))
    (is (= (nth errors 2) {:type "serde-fressian"
                           :category "ser"
                           :ErrorCode "UnsupportedCacheType"
                           :position 99}))))

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
   (js/Float64Array. #js[1 2 3])])

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


(defn mod-tests [Mod]
  (echo-test)
  (errors-test))


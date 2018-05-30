(ns fress.test-helpers
  (:require-macros [cljs.core.async.macros :refer [go-loop go]]
                   [fress.test-macros :as tm])
  (:require [cljs.test :as test :refer-macros [deftest is testing async]]
            [cljs-node-io.core :as io :refer [slurp spit]]
            [cljs.tools.reader :refer [read-string]]
            [fress.util :as util]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

(defn is=
  ([a b] (is (= a b)))
  ([a b c] (is (= a b c)))
  ([a b c d] (is (= a b c d))))

(let [arr (js/Int8Array. 1)]
  (defn overflow [n]
    (aset arr 0 n)
    (aget arr 0)))

(extend-type js/Int8Array
  IEquiv
  (-equiv [a b] (= (array-seq a) (array-seq b)))
  IIndexed
  (-nth
   ([arr n]
    (if (and (<= 0 n) (< n (.-length arr)))
      (aget arr n)
      (throw  (js/Error. "Index out of bounds"))))
   ([arr n not-found]
    (if (and (<= 0 n) (< n (.-length arr)))
      (aget arr n)
      not-found))))

(extend-type js/Int32Array
  IEquiv
  (-equiv [a b] (= (array-seq a) (array-seq b)))
  IIndexed
  (-nth
   ([arr n]
    (if (and (<= 0 n) (< n (.-length arr)))
      (aget arr n)
      (throw  (js/Error. "Index out of bounds"))))
   ([arr n not-found]
    (if (and (<= 0 n) (< n (.-length arr)))
      (aget arr n)
      not-found))))

(extend-type js/Float32Array
  IEquiv
  (-equiv [a b] (= (array-seq a) (array-seq b)))
  IIndexed
  (-nth
   ([arr n]
    (if (and (<= 0 n) (< n (.-length arr)))
      (aget arr n)
      (throw  (js/Error. "Index out of bounds"))))
   ([arr n not-found]
    (if (and (<= 0 n) (< n (.-length arr)))
      (aget arr n)
      not-found))))

(extend-type js/Float64Array
  IEquiv
  (-equiv [a b] (= (array-seq a) (array-seq b)))
  IIndexed
  (-nth
   ([arr n]
    (if (and (<= 0 n) (< n (.-length arr)))
      (aget arr n)
      (throw  (js/Error. "Index out of bounds"))))
   ([arr n not-found]
    (if (and (<= 0 n) (< n (.-length arr)))
      (aget arr n)
      not-found))))

(extend-type array
  IEquiv
  (-equiv [a b] (= (array-seq a) (array-seq b))))

(defn byteseq [wrt]
  (-> (js/Int8Array. (.. wrt -raw-out -memory -buffer) 0 (.. wrt -raw-out -bytesWritten))
    array-seq))

(def ^:dynamic *eps* 0.00001)

(defn roughly=
  ([a b](roughly= a b *eps*))
  ([a b tolerance]
   (assert (and (number? a) (number? b)) "roughly= takes numbers")
   (assert (number? tolerance) "roughly= tolerance is NaN")
   (< (js/Math.abs (- a b)) tolerance)))

(defn nearly=
  ([a b](nearly= a b *eps*))
  ([a b eps]
   (let [absA (js/Math.abs a)
         absB (js/Math.abs b)
         d (js/Math.abs (- a b))]
     (if (== a b)
       true
       (if (or (zero? a) (zero? b) (< d util/F32_MIN_NORMAL))
         ;;;extremely close, relative error less meaningful
         (< d (* eps util/F32_MIN_NORMAL))
         (< (/ diff (js/Math.min (+ absA absB) util/F32_MAX_VALUE)) eps))))))

(defn precision=
  ([a b](precision= a b 8))
  ([a b sig]
   (= (.toPrecision a sig) (.toPrecision b sig))))

(defn float=
  "lol"
  [a b]
  (or (roughly= a b)
      (nearly= a b)
      (precision= a b)))


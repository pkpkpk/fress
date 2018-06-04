(ns fress.test-helpers
  (:require-macros [cljs.core.async.macros :refer [go-loop go]]
                   [fress.test-macros :as tm])
  (:require [cljs.test :as test :refer-macros [deftest is testing async]]
            [cljs-node-io.core :as io :refer [slurp spit]]
            [cljs.tools.reader :refer [read-string]]
            [fress.impl.raw-input :as rawIn]
            [fress.util :as util]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

(defn is=
  ([a b] (is (= a b)))
  ([a b c] (is (= a b c)))
  ([a b c d] (is (= a b c d)-)))

(defn seq=
  [as bs]
  (assert (seq as) (seq bs))
  (and (= (count as) (count bs))
       (every? true? (map = as bs))))

(defn _seq [coll]
  (if (goog.isArrayLike coll)
    (array-seq coll)
    (seq coll)))

(defn are-nums=
  "type flexible assertion that accepts two sequences of numbers
   and checks they are all the same. shortcuts when different length.
   convention is to put control bytes etc in first position"
  [control out]
  (let [control (_seq control)
        out (_seq out)]
    (assert (every? number? control) "every value must be a number")
    (assert (every? number? out) "every value must be a number")
    (if (not= (count control) (count out))
      (is (= (count control) (count out))
          (str "args are of different lengths : (count control) " (count control) " (count out) " (count out)))
      (if (seq= control out)
        (is true) ;;only go use index assertions when we know theres a problem
        (doseq [[i out_i] (map-indexed #(vector %1 %2) out)]
          (let [control_i (nth control i)]
            (when-not (= control_i out_i)
              (is (= control_i out_i)
                  (str "idx: " i " control["i"]: " control_i " out["i"] " out_i)))))))))

(let [arr (js/Int8Array. 1)]
  (defn overflow [n]
    (aset arr 0 n)
    (aget arr 0)))

(extend-type js/Int8Array
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

(defn byteseq [wrt]
  (-> (js/Int8Array. (.. wrt -raw-out -memory -buffer) 0 (.. wrt -raw-out -bytesWritten))
    array-seq))

(defn rawbyteseq [rdr]
  (let [raw (.-raw-in rdr)
        acc #js[]]
    (loop []
      (let [byte (try (rawIn/readRawByte raw)
                   (catch js/Error EOF
                     false))]
        (if-not byte
          (vec acc)
          (do
            (.push acc byte)
            (recur)))))))

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


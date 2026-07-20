(ns fress.impl.bigdec
  "A minimal, dependency-free arbitrary-precision decimal for ClojureScript,
   mirroring java.math.BigDecimal: an unscaled `js/BigInt` value and an integer
   `scale`, where the numeric value = unscaled x 10^(-scale).

   This is the canonical value produced by the Fressian BIGDEC (0xC7) reader and
   consumed by the BIGDEC writer — the exact counterpart of JVM Fressian's
   default BigDecimal support (and the sibling of fress's own bigint handling).
   Downstream consumers that need decimal arithmetic (e.g. a bignumber.js-backed
   money type) convert to/from `Bigdec` via `->unscaled` / `->scale` / `bigdec`."
  (:require [fress.impl.bigint :as bn]))

(defn- render
  "Render (unscaled x 10^-scale) as a canonical decimal string."
  [unscaled scale]
  (let [negative? (< unscaled 0)
        digits    (.toString (bn/abs unscaled))
        body (cond
               (< scale 0) (str digits (apply str (repeat (- scale) \0)))
               (== scale 0) digits
               :else
               (let [digits (if (<= (count digits) scale)
                              ;; pad so there is at least one integer digit
                              (str (apply str (repeat (- (inc scale) (count digits)) \0)) digits)
                              digits)
                     n (count digits)]
                 (str (subs digits 0 (- n scale)) "." (subs digits (- n scale)))))]
    (str (when negative? "-") body)))

(deftype Bigdec [unscaled scale]
  Object
  (toString [_] (render unscaled scale))
  IEquiv
  (-equiv [_ o]
    (and (instance? Bigdec o)
         ;; compare BigInts as strings — cljs = / == are unreliable on js/BigInt
         (= (.toString unscaled) (.toString (.-unscaled o)))
         (== scale (.-scale o))))
  IHash
  (-hash [_] (hash [(.toString unscaled) scale])))

(defn bigdec
  "Construct a Bigdec from an unscaled `js/BigInt` and an integer scale."
  [unscaled scale]
  (->Bigdec unscaled scale))

(defn bigdec?
  [x]
  (instance? Bigdec x))

(defn ->unscaled ^js/BigInt [^Bigdec bd] (.-unscaled bd))
(defn ->scale    ^number    [^Bigdec bd] (.-scale bd))

(defn ->str [^Bigdec bd] (render (.-unscaled bd) (.-scale bd)))

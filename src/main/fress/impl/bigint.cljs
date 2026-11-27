(ns fress.impl.bigint
  (:refer-clojure :exclude [bit-not abs]))

(defn bigint?
  [n]
  (identical? js/BigInt (type n)))

(defn bigint
  [n]
  (js/BigInt n))

(defn abs
  [bn]
  (if (< bn 0)
    (- bn)
    bn))

(defn pow
  [base exponent]
  (js* "~{} ** BigInt(~{})" base exponent))

(defn >>
  [a b]
  (js* "~{} >> BigInt(~{})" a b))

(defn <<
  [a b]
  (js* "~{} << BigInt(~{})" a b))

(defn ^number bit-switch
  "@return {number} bits not needed to represent this number"
  [bn]
  (if (js* "~{} === 0n" bn)
    64
    (let [magnitude (if (< bn 0) (js* "(~{} ^ -1n)" bn) bn)]
      (- 64 (.-length (.toString magnitude 2))))))

;;==============================================================================

(defn ^string flip-bin-string [^string bin]
  (.join (.map (.split bin "") (fn [i] (if (identical? "0" i) "1" "0"))) ""))

(defn hex->signed-bigint
  "interprets as signed integer, use js/BigInt directly for unsigned"
  [hex]
  (let [byte-count (js/Math.ceil (/ (count hex) 2))]
    (js/BigInt.asIntN (* 8 byte-count) (bigint (str "0x" hex)))))

(defn bytes->bigint [bytes]
  (let [hex #js []]
    (.forEach (js/Uint8Array.from bytes)
              (fn [b] (.push hex (.padStart (.toString b 16) 2 "0"))))
    (hex->signed-bigint (.join hex ""))))

(defn bigint->hex [value]
  ;; Minimal signed big-endian representation, including a sign bit even
  ;; when the unsigned magnitude ends on a byte boundary.
  (let [bn (bigint value)
        magnitude (if (< bn 0) (js* "(~{} ^ -1n)" bn) bn)
        bits (inc (.-length (.toString magnitude 2)))
        byte-count (js/Math.ceil (/ bits 8))
        unsigned (js/BigInt.asUintN (* 8 byte-count) bn)]
    (.padStart (.toString unsigned 16) (* 2 byte-count) "0")))

(defn bigint->bytes [bn]
  (let [hex (bigint->hex bn)
        bytes (js/Uint8Array. (/ (count hex) 2))]
    (dotimes [i (alength bytes)]
      (let [j (* 2 i)]
        (aset bytes i (js/parseInt (.slice hex j (+ j 2)) 16))))
    bytes))

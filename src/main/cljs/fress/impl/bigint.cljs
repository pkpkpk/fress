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
    (let [bn bn]
      (if (< bn 0)
        (do
          (js* "~{} = BigInt(~{}) + BigInt(1)" bn bn)
          (js* "~{} = -~{}" bn bn)))
      (- 64 (.-length (.toString bn 2))))))

;;==============================================================================

(defn ^string flip-bin-string [^string bin]
  (.join (.map (.split bin "") (fn [i] (if (identical? "0" i) "1" "0"))) ""))

(defn hex->signed-bigint
  "interprets as signed integer, use js/BigInt directly for unsigned"
  [hex]
  (set! *unchecked-if* true)
  (let [hex (if (mod (alength hex) 2)
              (str "0" hex)
              hex)
        high-byte (js/parseInt (.slice hex 0 2) 16)
        bn (js/BigInt (str "0x" hex))]
    ;; if negative number, flip bits & add 1 (because js operators are for unsigned only)
    (if (bit-and 0x80 high-byte)
      (let [bin  (str "0b" (flip-bin-string (.toString bn 2)))]
        (js* "~{} = -( BigInt(~{}) + 1n)" bn bin)))
    (set! *unchecked-if* false)
    bn))

(defn bytes->bigint [bytes]
  (let [hex #js[]
        bytes (js/Uint8Array.from bytes)]
    (.forEach bytes
      (fn [i]
        (let [h (.toString i 16)]
          (set! *unchecked-if* true)
          (if (mod (alength h) 2)
            (js* "~{} = '0' + ~{}" h h))
          (set! *unchecked-if* false)
          (.push hex h))))
    (hex->signed-bigint (.join hex ""))))

(defn- bit-not [bn]
  (let [bin (.toString (- bn) 2)
        _(do
           (set! *unchecked-if* true)
           (while (mod (alength bin) 8)
             (js* "bin = '0' + bin"))
           (set! *unchecked-if* false))
        prefix (when (and (identical? "1" (aget bin 0))
                          (not (== -1 (.indexOf (.slice bin 1) "1"))))
                 "11111111")]
    (unchecked-add (bigint (str "0b" prefix (flip-bin-string bin))) (bigint 1))))

(defn bigint->hex
  [bn]
  (let [bn (bigint bn)
        pos (if (< bn 0)
               (do
                 (js* "~{} = ~{}" bn (bit-not bn))
                 false)
               true)
        hex (.toString bn 16)]
    (set! *unchecked-if* true)
    (if (mod (alength hex) 2)
      (js* "hex = '0' + hex"))
    (if (and pos (bit-and 0x80 (js/parseInt (.slice hex 0 2) 16)))
      (js* "hex = '00' + hex"))
    (set! *unchecked-if* false)
    hex))

(defn bigint->bytes [bn]
  (set! *unchecked-if* true)
  (let [hex (bigint->hex bn)
        _(if (mod (alength hex) 2)
           (js* "hex = '0' + hex"))
        byte-length (/ (alength hex) 2)
        bytes (js/Uint8Array. byte-length)]
    (loop [i 0 j 0]
      (when (< i byte-length)
        (aset bytes i (js/parseInt (.slice hex j (+ j 2)) 16))
        (recur (inc i) (unchecked-add j 2))))
    (set! *unchecked-if* false)
    bytes))

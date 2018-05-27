(ns fress.util)

(extend-type ArrayList
  Object
  (get [this i] (aget (.-arr this) i)))

(def FLOAT_MIN_NORMAL 1.17549435E-38)
(def FLOAT_MAX_VALUE 3.4028235E38)
(def U32_MAX_VALUE (js* "2**32 -1"))

(def isBigEndian
  (-> (.-buffer (js/Uint32Array. #js[0x12345678]))
    (js/Uint8Array. )
    (aget 0)
    (== 0x12)))

(defn expected
  [rdr tag code o]
  (let [index (.-raw-in rdr)
        msg (str "Expected " tag " with code: " code "prior to index: " index
                 ", got " (type o) " " (pr-str o) "instead")]
    (throw (js/Error. msg))))

(defmulti byte-array type)

(defmethod byte-array js/Number [n]
  (js/Int8Array. n))

(defmethod byte-array cljs.core/PersistentVector [v]
  (js/Int8Array. (into-array v)))

(defmethod byte-array js/Array [a]
  (js/Int8Array. a))

(defmethod byte-array ArrayList [al]
  (js/Int8Array. (.toArray al)))
(ns fress.util)

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
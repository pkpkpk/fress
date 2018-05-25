(ns fress.util)

(def isBigEndian
  (-> (.-buffer (js/Uint32Array. #js[0x12345678]))
    (js/Uint8Array. )
    (aget 0)
    (== 0x12)))

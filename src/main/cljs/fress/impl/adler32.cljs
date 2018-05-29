(ns fress.impl.adler32)

(defprotocol Adler32Protocol
  (update! [this byte]
           [this bytes offset length])
  (get-value [this])
  (reset [this]))

(def ^:const adler32-base 65521)

(defrecord Adler32 [value]
  IDeref
  (-deref [this] (bit-and value 0xffffffff))
  Adler32Protocol
  (update! [this byte]
    (let [s1 (+ (bit-and value 0xffff) (bit-and byte 0xff))
          s2 (+ (bit-and (bit-shift-right value 16) 0xffff) s1)]
      (set! (.-value this)
        (bit-or (bit-shift-left (mod s2 adler32-base) 16)
                (mod s1 adler32-base)))))
  (update! [this bs off len]
    (doseq [i (range off (+ off len))]
      (update! this (aget bs i))))
  (reset [this] (set! (.-value this) 1)))

(defn adler32 [] (Adler32. 1))

(ns fress.hopmap
  (:require-macros [fress.macros :refer [<<]]))

; a hashmap that uses open-addressing,
; and the InterleavedIndexHopMap describes how to deal with collisions

(defprotocol IHopMap
  (clear [this])
  (oldIndex [this k])
  (isEmpty [this])
  (intern [this k])
  (resize [this])
  (findSlot [this ]))

(defn _hash [ k]
  (let [h (hash k)]
    (if (zero? h) ; reserve 0 for no-entry
      42
      h)))

(defn _get
  "@param k, non-null
   @return the integer associated with k, or -1 if not present"
  [this k]
  (assert (some? k))
  (let [hopidx (.-hopidx this)
        keys (.-keys this)
        hsh (_hash k)
        mask (dec (.-cap this))
        bkt (bit-and hsh mask)
        bhash (aget hopidx (<< bkt 2))]
    (assert (and (int? hsh) (int? mask) (int? bkt) (int? bhash)))
    (or
      (and (not= 0 bhash)
        (let [bhash+1 (aget hopidx (inc (<< bkt 2)))
              bkey (aget keys bhash+1)]
          (if (and (= hsh bhash) (= k bkey))
            bhash+1
            (loop [bhash (aget hopidx (+ (<< bkt 2) 2))
                   bkt (bit-and (inc bkt) mask)]
              (when-not (zero? bhash)
                (let [i (aget hopidx (+ (<< bkt 2) 3))
                      bkey (aget keys i)]
                  (if (and (= hsh bhash) (= bkey k))
                    idx
                    (recur
                      (aget hopidx (+ (<< bkt 2) 2))
                      (bit-and (inc bkt) mask)))))))))
      -1)))

(defn _clear [this]
  (set! (.-count this) 0)
  (dotimes [i (.-cap this)]
    (aset (.-keys this) i nil))
  (let [cap2 (<< cap 2)]
    (dotimes [i cap2]
      (aset (.-hopidx n) i 0))))

(defn _oldIndex
  "Puts k in the map if it was not already present.
   Returns -1 if k was freshly added
   Returns k's index if k was already in the map.
   @param k, non-null
   @return the integer associated with k or -1"
  [this k]
  (let [countBefore (.-count this)
        index (intern this k)]
    (assert (int? countBefore))
    (assert (int? index))
    (if (== countBefore (.-count this))
      index ; already present
      -1)))



; cap => int
; hopidx => int-array
; keys => object-array
; count => int 0
(deftype InterleavedIndexHopMap
  [^number cap ^array hopidx ^array keys ^number count]
  ILookup
  (-lookup [this k] (_get this k))
  IHopMap
  (isEmpty [this] (zero? count))
  (clear [this] (_clear this))
  (oldIndex [this k] (_oldIndex this k))
  (intern [this k])
  (resize [this])
  (findSlot [this ]))

(defn hopmap
  ([](hopmap 16))
  ([capacity]

   (let [
         ]
     (InterleavedIndexHopMap. nil nil nil nil))))
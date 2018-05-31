(ns fress.impl.hopmap
  (:require-macros [fress.macros :refer [<<]]))

; a hashmap that uses open-addressing,
; and the InterleavedIndexHopMap describes how to deal with collisions

(defprotocol IHopMap
  (clear [this])
  (oldIndex [this k])
  (isEmpty [this])
  (intern [this k])
  (resize [this])
  (findSlot [this hash]))

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

(defn ^number _oldIndex
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

(defn ^number _intern
  "Puts k in the map (if not present) and assigns and returns the index associated with it
   assigns ints monotonically from 0
   @param k, non-null
   @return the integer associated with k"
  [this k]
  {:pre [(some? k)] :post [int?]}
  (let [hopidx (.-hopidx this)
        keys (.-keys this)
        cap (.-cap this)

        hash (_hash k)
        mask (dec cap)
        bkt (bit-and hash mask)
        bhash (aget hopidx (<< bkt 2))
        _(assert (and (int? hash) (int? mask) (int? bkt) (int? bhash)))
        slot (atom 0)
        _(set-validator! slot int?)
        bkey (atom nil)]
    (if (zero? bhash)
      (reset! slot (<< bkt 2))
      (or
        (and (== hash bhash)
          (let [item (aget hopidx (inc (<< bkt 2)))]
            (reset! bkey (aget keys item))
            (when (== k @bkey)
              item)))
        (loop [bhash (aget hopidx (+ (<< bkt 2) 2))
               bkt (bit-and (inc bkt) mask)]
          (when-not (zero? bhash)
            (if (== hash bhash)
              (let [idx (aget hopidx (+ (<< bkt 2) 3))]
                (reset! bkey (aget keys idx))
                (if (== k @bkey)
                  idx
                  (recur
                    (aget hopidx (+ (<< bkt 2) 2))
                    (bit-and (inc bkt) mask))))
              (recur
                (aget hopidx (+ (<< bkt 2) 2))
                (bit-and (inc bkt) mask)))))
        (do
          (reset! slot (+ 2 (<< bkt 2)))
          (let [i (.-count this)]
            (aset hopidx @slot hash)
            (aset hopidx (inc @slot) i)
            (aset keys i k)
            (when (== (.-count this) cap)
              (resize this))
            i))))))


(defn ^number _findSlot
  [this hash]
  {:pre [(int? hash)] :post [int?]}
  (let [hopidx (.-hopidx this)
        cap (.-cap this)
        mask (dec cap)
        bkt (bit-and hash mask)
        bhash (aget hopidx (<< bkt 2))]
    (assert (and (int? mask) (int? bkt) (int? bhash)))
    (if (zero? bhash)
      (<< bkt 2)
      (let [slot (atom 0)
            _(set-validator! slot int?)]
        (loop [idx (aget hopidx (+ (<< bkt 2) 2))
               bkt (bit-and (inc bkt) mask)]
          (if (zero? bkt)
            @slot
            (do
              (reset! slot (+ (<< bkt 2) 2))
              (recur
                (aget hopidx (+ (<< bkt 2) 2))
                (bit-and (inc bkt) mask)))))))))

(defn _resize
  [this]
  (let [oldhops (.-hopidx this)
        _(set! (.-hopidx this) (make-array (* 2 (alength oldhops))))
        _(set! (.-cap this) (<< (.-cap this) 1))
        _(set! (.-length (.-keys this)) (.-cap this))]
    (loop [slot 0]
      (when (< slot (alength oldhops))
        (let [item (aget oldhops slot)
              new-slot (findSlot this item)]
          (aset hopidx new-slot item)
          (aset hopidx (inc new-slot) (aget oldhops (inc slot)))
          (recur (+ 2 slot)))))))

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
  (oldIndex ^number [this k] (_oldIndex this k))
  (intern ^number [this k] (_intern this k))
  (resize [this] (_resize this))
  (findSlot ^number [this h] (_findSlot this h)))

(defn hopmap
  ([](hopmap 1024))
  ([capacity]
   (let [cap (atom 1)
         _ (while (< @cap capacity)
             (swap! cap #(<< % 1)))
         cap @cap
         hopidx (make-array (<< cap 2)) ;; [hash, idx of key, collision hash, collision idx, ...]
         keys (make-array cap)]
     (InterleavedIndexHopMap. cap hopidx keys 0))))
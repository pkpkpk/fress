(ns fress.impl.hopmap
  (:require-macros [fress.macros :refer [<<]])
  (:require [fress.util :refer [i32-array]]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

; a hashmap that uses open-addressing,
; and the InterleavedIndexHopMap describes how to deal with collisions

(defprotocol IHopMap
  (clear [this])
  (oldIndex [this k])
  (isEmpty [this])
  (intern [this k])
  (resize [this])
  (findSlot [this hash]))

(defn _hash [k]
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
        _(assert (int? hash)  (str "hash should be an int, got" (pr-str hash)))
        _(assert (int? mask)  (str "mask should be an int, got" (pr-str mask)))
        _(assert (int? bkt)   (str "bkt should be an int, got" (pr-str bkt)))
        _(assert (int? bhash) (str "bhash should be an int, got" (pr-str bhash)))
        slot (atom 0)
        _(set-validator! slot int?)]
    (when (zero? bhash) ; not found hopidx lookup
      (reset! slot (<< bkt 2)))
    (or
      (when-not (zero? bhash) ; found value at (aget hopidx (<< bkt 2))
        (or
          ;; if hash is identical && stored object is identical
          ;; return key-index of (already) interned object
          (and (== hash bhash)
            (let [key-index (aget hopidx (inc (<< bkt 2)))]
              (when (= k (aget keys key-index))
                key-index)))

          ;; theres is an item found at (aget hopidx (<< bkt 2)) but either:
          ;;   - the hash stored at hopidx[(<< bkt 2)] is not identical to k's hash
          ;;   - the hash is identical but the object is not
          ;; so we shift (bit-and hash mask) until zero, looking up the stored
          ;; object for each to see if we already have interned it
          (let [bkt (atom bkt)
                increment-bkt #(swap! bkt (fn [n] (bit-and (inc n) mask)))]
            (loop [bhash (aget hopidx (+ (<< @bkt 2) 2))]
              (when-not (zero? bhash)
                (if (== hash bhash)
                  (let [key-index (aget hopidx (+ (<< @bkt 2) 3))]
                    (if (= k (aget keys key-index))
                      key-index
                      (do
                        (increment-bkt)
                        (recur (aget hopidx (+ (<< @bkt 2) 2))))))
                  (do
                    (increment-bkt)
                    (recur (aget hopidx (+ (<< bkt 2) 2))))))))
          (do
            (reset! slot (+ 2 (<< bkt 2)))
            nil)))
     (let [i (.-count this)] ;new item
       (dbg "interning " k " hash " hash "at @slot " @slot ", k[i] at " (inc @slot))
       (aset hopidx @slot hash) ; 884087478
       (dbg "(aget hopidx @slot)" (aget hopidx @slot))
       (aset hopidx (inc @slot) i) ;39
       (aset keys i k)
       (dbg "(aget keys i)" (aget keys i) )
       (set! (.-count this) (inc (.-count this)))
       (when (== (.-count this) cap)
         (resize this))
       i))))


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
        _(set! (.-hopidx this) (i32-array (* 2 (alength oldhops))))
        _(set! (.-cap this) (<< (.-cap this) 1))
        _(set! (.-length (.-keys this)) (.-cap this))]
    (loop [slot 0]
      (when (< slot (alength oldhops))
        (let [item (aget oldhops slot)
              new-slot (findSlot this item)]
          (aset (.-hopidx this) new-slot item)
          (aset (.-hopidx this) (inc new-slot) (aget oldhops (inc slot)))
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
         hopidx (i32-array (<< cap 2)) ;; [hash, idx of key, collision hash, collision idx, ...]
         keys (make-array cap)]
     (InterleavedIndexHopMap. cap hopidx keys 0))))
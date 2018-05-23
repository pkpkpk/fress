(ns fress.hopmap)

; (defrecord TaggedObject [tag value meta])

; (defrecord StructType [tag fields])

#_(defprotocol InterleavedIndexHoppable
  (old-index [this k])
  (isEmpty [this])
  (clear [this]))

#_(defrecord InterleavedIndexHopMap [ks hash-indexes]
  InterleavedIndexHoppable
  (old-index [_ k]
    (let [h (hash k)]
      (if-let [idx (get @hash-indexes h)]
        idx
        (do (swap! ks conj k)
            (swap! hash-indexes assoc h (dec (count @ks)))
            -1)))))

; (defn hopmap
;   ([](InterleavedIndexHopMap. (atom []) (atom {})))
;   ([capacity] (InterleavedIndexHopMap. (atom []) (atom {}))))


(defprotocol IHopMap
  (clear [this])
  (oldIndex [this k])
  (isEmpty [this])
  (intern [this k])
  (resize [this])
  (findSlot [this ]))

(defn- _hash [this k]
  (let [h (hash k)]
    (if (zero? h) ; reserve 0 for no-entry
      42
      h)))

; cap => int
; hopidx => int?
; keys => Object
; count => 0

(deftype HopMap [cap hopidx keys count]
  ILookup
  (-lookup [this k])
  IHopMap
  (clear [this]))

(defn hopmap
  ([](hopmap 16))
  ([capacity]
   (let [cap (atom 1)
         _ (while (< @cap capacity)
             (swap! cap ))]
     (HopMap. nil nil nil nil))))
(ns fress.hopmap)

; (defrecord TaggedObject [tag value meta])

; (defrecord StructType [tag fields])

(defprotocol InterleavedIndexHoppable
  (old-index [this k])
  (isEmpty [this])
  (clear [this]))

(defrecord InterleavedIndexHopMap [ks hash-indexes]
  InterleavedIndexHoppable
  (old-index [_ k]
    (let [h (hash k)]
      (if-let [idx (get @hash-indexes h)]
        idx
        (do (swap! ks conj k)
            (swap! hash-indexes assoc h (dec (count @ks)))
            -1)))))

(defn hopmap
  ([](InterleavedIndexHopMap. (atom []) (atom {})))
  ([capacity] (InterleavedIndexHopMap. (atom []) (atom {}))))
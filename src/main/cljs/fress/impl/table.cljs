(ns ^{:doc
  "Array.indexOf(<element>) uses === which is sufficient for handler lookup.
   Faster than cljs hashmaps + drags in much less code where possible.
   #js[k_n, v_n, k_n+1, v_n+1, ...]"}
  fress.impl.table)

(deftype HandlerTable [a])

(defn- ?get
  [^HandlerTable table k]
  (let [i (.indexOf (.-a table) k)]
    (when (> i -1)
      (aget (.-a table) (inc i)))))

(defn- _set
  [^HandlerTable table k v]
  (let [i (.indexOf (.-a table) k)]
    (if (> i -1)
      (do ;;overwrite existing value
        (aset (.-a table) (inc i) v)
        table)
      (do ;; add new entry
        (.push (.-a table) k)
        (.push (.-a table) v)
        table))))

(defn- add-handler
  [^HandlerTable table [k handler]]
  (if (coll? k)
    ;; allow multiple keys pointing to same handler
    (reduce (fn [_ k] (_set table k handler)) table k)
    (.set table k handler)))

(defn- _keys ;=> #js[]
  [^HandlerTable table]
  ;;seq of items at even indexes from 0 to len-2
  (let [len (alength (.-a table))
        acc #js[]]
    (loop [i 0]
      (when (<= i (- len 2))
        (.push acc (aget (.-a table) i))
        (recur (+ i 2))))
    acc))

(extend-type HandlerTable
  Object
  (?get [this item] (?get this item))
  (set [this k v] (_set this k v))
  (keys [this] (_keys this))
  (add-handlers [this handlers] (reduce add-handler this handlers)))

(defn ^HandlerTable from-array [arr] (HandlerTable. arr))

(defn ^HandlerTable from-table [^HandlerTable t] (HandlerTable. (.slice (.-a t) 0)))

(ns fress.wasm
  (:require [fress.api :as api]
            [fress.util :as util]))

(defn wasm-module? [o] (instance? js/WebAssembly.Instance o))

(defn assert-fress-mod! [Mod]
  (assert (wasm-module? Mod))
  (assert (some? (.. Mod -exports -fress_alloc)))
  ; (assert (some? (.. Mod -exports -fress_free)))
  (assert (some? (.. Mod -exports -memory))))

(defn write-bytes [Mod bytes] ;=> ptr
  (assert-fress-mod! Mod)
  (assert (#{js/Uint8Array js/Int8Array} (type bytes)))
  (let [ptr ((.. Mod -exports -fress_alloc) (alength bytes))
        view (js/Uint8Array. (.. Mod -exports -memory -buffer))]
    (.set view bytes ptr)
    ptr))


(defrecord Error [category position])

;; should there be Ok(value) type that returns read length so
;; it can be freed?

(defn read-all [Mod ptr]
  (assert-fress-mod! Mod)
  (assert (util/valid-pointer? ptr))
  (let [res (fress.api/read-all (.. Mod -exports -memory)
                                :offset ptr
                                ;; bake-in
                                :handlers {"error" (fn [rdr tag fields]
                                                     (map->Error (api/read-object rdr)))})]
    ;; ...free....
    (if (instance? Error res)
      [res]
      [nil res])))

(defn write [v]) ;; stringify-keys

(ns fress.wasm
  (:require [fress.api :as api]
            [fress.impl.codes :as codes]
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

(defn read-all [Mod ptr]
  (assert-fress-mod! Mod)
  (assert (util/valid-pointer? ptr))
  (let [view (js/Uint8Array. (.. Mod -exports -memory -buffer))
        result (fress.api/read-all view :offset ptr)]
    ;; ...free....
    (if (== codes/ERROR (aget view ptr))
      [result]
      [nil result])))

(defn write [v]) ;; stringify-keys

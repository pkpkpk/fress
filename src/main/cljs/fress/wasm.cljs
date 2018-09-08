(ns fress.wasm
  (:require [fress.api :as api]))

(defn assert-fress-wasm! [Mod]
  (assert (some? (.. Mod -exports -fress_alloc)))
  (assert (some? (.. Mod -exports -memory))))

(defn write-bytes [Mod bytes] ;=> ptr
  (assert-fress-wasm! Mod)
  (assert (#{js/Uint8Array js/Int8Array} (type bytes)))
  (let [ptr ((.. Mod -exports -fress_alloc) (alength bytes))
        view (js/Uint8Array. (.. Mod -exports -memory -buffer))]
    (.set view bytes ptr)
    ptr))

(deftype Error [])

(defn read-bytes [Mod ptr]) ;=> [?Error ?Value]

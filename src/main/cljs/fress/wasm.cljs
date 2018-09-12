(ns fress.wasm
  (:require [fress.api :as api]
            [fress.reader :as r]
            [fress.impl.codes :as codes]
            [fress.impl.buffer :as buf]
            [fress.impl.raw-input :as rawIn]
            [fress.util :as util]))

(defn wasm-module? [o] (instance? js/WebAssembly.Instance o))

(defn assert-fress-mod! [Mod]
  (assert (wasm-module? Mod))
  (assert (some? (.. Mod -exports -fress_alloc)))
  (assert (some? (.. Mod -exports -fress_free)))
  (assert (some? (.. Mod -exports -memory))))

(defn write-bytes [Mod bytes] ;=> ptr
  (assert-fress-mod! Mod)
  (assert (#{js/Uint8Array js/Int8Array} (type bytes)))
  (let [ptr ((.. Mod -exports -fress_alloc) (alength bytes))
        view (js/Uint8Array. (.. Mod -exports -memory -buffer))]
    (.set view bytes ptr)
    ptr))


; will break on imported memory, add :memory option or check imports
(defn read-all
  "Given a WASM module, a pointer, and opts, read off fressian objects and
   automatically free the used memory.
     - Call this synchronously after obtaining the ptr and before any other
       calls on the same module/memory
     - right now opts is :handlers only
     - no :checksum? (not in rust yet)
     - no :name->map-ctor (no records in rust yet)"
  [Mod ptr & {:keys [handlers] :or {handlers nil}}]
  (assert-fress-mod! Mod)
  (assert (util/valid-pointer? ptr))
  (let [memory (or (.. Mod -exports -memory) (.. Mod -imports -memory)) ; env?
        _(assert (some? memory))
        view (js/Uint8Array. (.-buffer memory))
        rdr (r/reader view :offset ptr :handlers handlers)
        result (fress.api/read-batch rdr)
        bytes_read (rawIn/getBytesRead (get rdr :raw-in))
        ret (if (== codes/ERROR (aget view ptr))
              [result]
              [nil result])]
    ((.. Mod -exports -fress_free) ptr bytes_read) ;; dealloc after reading
    ret))


; (defonce _write-buffer)

(defn write [v]) ;; stringify-keys

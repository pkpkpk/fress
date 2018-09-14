(ns fress.wasm
  (:require [fress.api :as api]
            [fress.reader :as r]
            [fress.writer :as w]
            [fress.impl.codes :as codes]
            [fress.impl.buffer :as buf]
            [fress.impl.raw-input :as rawIn]
            [fress.util :as util :refer [log]]))

(defn wasm-module? [o] (instance? js/WebAssembly.Instance o))

(defn assert-fress-mod! [Mod]
  (assert (wasm-module? Mod))
  (assert (some? (.. Mod -exports -fress_alloc)))
  (assert (some? (.. Mod -exports -fress_free)))
  (assert (some? (.. Mod -exports -memory))))

; will break on imported memory
(defn read-all
  "Given a WASM module, a pointer, and opts, read off fressian objects and
   automatically free the used memory. Call this synchronously after
   obtaining the ptr and before any other calls on the same module/memory
     + currently opts is :handlers only
       - no :checksum? or :name->map-ctor (not in rust yet)
   => [?err ?[result..]]"
  [Mod ptr & {:keys [handlers]}]
  (assert-fress-mod! Mod)
  (assert (util/valid-pointer? ptr))
  (let [memory (or (.. Mod -exports -memory) (.. Mod -imports -memory)) ; env?
        _(assert (some? memory))
        view (js/Uint8Array. (.-buffer memory))
        rdr (r/reader view :offset ptr :handlers handlers)
        ;; relying on footer induced EOF in absence of knowing length
        result (fress.api/read-batch rdr)
        bytes_read (rawIn/getBytesRead (get rdr :raw-in))
        ret (if (== codes/ERROR (aget view ptr))
              [result]
              [nil result])]
    ((.. Mod -exports -fress_free) ptr bytes_read) ;; dealloc after reading
    ret))

(defn write-bytes
  "Given a WASM module and a byte array, write the bytes into memory and return
   a tuple containing a pointer and the length of the bytes written. The
   pointer+length should be given synchronously to an exported wasm function to
   claim and deserialize fressian data.
   => [pointer length]"
  [Mod bytes]
  (assert-fress-mod! Mod)
  (assert (goog.isArrayLike bytes))
  (let [ptr ((.. Mod -exports -fress_alloc) (alength bytes))
        view (js/Uint8Array. (.. Mod -exports -memory -buffer))]
    (.set view bytes ptr)
    [ptr (alength bytes)]))

(def write
  "Given a WASM module, object, and opts, write the object into wasm memory
   and return a tuple containing a pointer and the length of the bytes written.
   The pointer+length should be given synchronously to an exported wasm
   function to claim and deserialize fressian data.
     + currently opts is :handlers only, no :record->name, :checksum?
   => [pointer length]"
  (let [buffer (buf/with_capacity 128)]
    (fn [Mod obj & {:keys [handlers]}]
      (assert-fress-mod! Mod)
      (binding [w/*stringify-keys* true]
        (let [writer (w/writer buffer :handlers handlers)
              _ (w/writeObject writer obj)
              byte-length (buf/getBytesWritten buffer)
              ptr ((.. Mod -exports -fress_alloc) byte-length)
              view (js/Uint8Array. (.. Mod -exports -memory -buffer))]
          (buf/flushTo buffer view ptr)
          (buf/reset buffer)
          [ptr byte-length])))))

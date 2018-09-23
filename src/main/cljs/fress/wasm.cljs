(ns fress.wasm
  (:require [fress.reader :as r]
            [fress.writer :as w]
            [fress.impl.codes :as codes]
            [fress.impl.buffer :as buf]
            [fress.impl.raw-input :as rawIn]
            [fress.util :as util :refer [log]]))

(defn assert-fress-mod! [Mod]
  (assert (instance? js/WebAssembly.Instance Mod))
  (assert (some? (.. Mod -exports -fress_alloc)))
  (assert (some? (.. Mod -exports -fress_dealloc)))
  (assert (some? (.. Mod -exports -memory))))

; will break on imported memory
(defn read-object
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
        ret (if (== codes/ERROR (aget view ptr))
              [(r/readObject rdr)]
              [nil (r/readObject rdr)])
        bytes_read (rawIn/getBytesRead (get rdr :raw-in))]
    ((.. Mod -exports -fress_dealloc) ptr bytes_read) ;; dealloc after reading
    ret))

(defonce ^:private _buffer (buf/with_capacity 128))

(defn write-object
  "Given a WASM module, object, and opts, write the object into wasm memory
   and return a tuple containing a pointer and the length of the bytes written.
   The pointer+length should be given synchronously to an exported wasm
   function to claim and deserialize fressian data.
     + opts
       - :handlers {type write-handler}
       - :stringify-keys bool
       - no :record->name, :checksum?
   => [pointer length]"
  [Mod obj & {:keys [handlers stringify-keys]}]
  (assert-fress-mod! Mod)
  (let [writer (w/writer buffer :handlers handlers)
        _ (binding [w/*write-raw-utf8* false ;; somehow slower
                    w/*stringify-keys* (or w/*stringify-keys* stringify-keys)]
            (w/writeObject writer obj))
        byte-length (buf/getBytesWritten _buffer)
        ptr ((.. Mod -exports -fress_alloc) byte-length)
        view (js/Uint8Array. (.. Mod -exports -memory -buffer))]
    (buf/flushTo _buffer view ptr)
    (buf/reset _buffer)
    [ptr byte-length]))



(defprotocol IFressWasmModule
  (get-view [Mod])
  (get-memory [Mod])
  (get-exports [Mod])
  (get-imports [Mod])
  (alloc [Mod len]) ;=> ptr
  (dealloc [Mod ptr len])
  ; (read-bytes [Mod])
  (copy-bytes [Mod ptr len])
  (write-bytes [Mod bytes])
  ;;;;;;;;;;;;;;;;;;;;;;;
  (read [Mod ptr] [Mod ptr opts])
  (write [Mod any] [Mod any opts]))

(defn- module-read
  [Mod ptr {:keys [handlers]}]
  (assert (util/valid-pointer? ptr) (str "wasm/read given invalid pointer : '" (pr-str ptr) "'"))
  (let [memory (get-memory Mod)
        _(assert (some? memory))
        view (get-view Mod)
        rdr (r/reader view :offset ptr :handlers handlers)
        ret (if (== codes/ERROR (aget view ptr))
              [(r/readObject rdr)]
              [nil (r/readObject rdr)])
        bytes_read (rawIn/getBytesRead (get rdr :raw-in))]
    (dealloc Mod ptr bytes_read)
    ret))

(defn- module-write
  [Mod obj {:keys [handlers stringify-keys]}]
  (let [writer (w/writer _buffer :handlers handlers)
        _ (binding [w/*write-raw-utf8* false
                    w/*stringify-keys* (or ^boolean w/*stringify-keys* ^boolean stringify-keys)]
            (w/writeObject writer obj))
        byte-length (buf/getBytesWritten _buffer)
        ptr ((.. Mod -exports -fress_alloc) byte-length)
        view (js/Uint8Array. (.. Mod -exports -memory -buffer))]
    (buf/flushTo _buffer view ptr)
    (buf/reset _buffer)
    [ptr byte-length]))

(defn module-write-bytes
  "Given a WASM module and a byte array, write the bytes into memory and return
   a tuple containing a pointer and the length of the bytes written. The
   pointer+length should be given synchronously to an exported wasm function to
   claim and deserialize fressian data.
   => [pointer length]"
  [Mod bytes]
  (assert (goog.isArrayLike bytes))
  (let [ptr ((.. Mod -exports -fress_alloc) (alength bytes))
        view (js/Uint8Array. (.. Mod -exports -memory -buffer))]
    (.set view bytes ptr)
    [ptr (alength bytes)]))

(defn attach-protocol! [Mod]
  (assert-fress-mod! Mod)
  (let []
    (specify! Mod
      ; ILookup
      ; (-lookup [])
      IFressWasmModule
      (get-view [_]
        (js/Uint8Array. (.. Mod -exports -memory -buffer)))
      (get-memory [_]
        (or (.. Mod -exports -memory) (.. Mod -imports -memory)))
      (get-exports [_] (.-exports Mod))
      (get-imports [_] (.-imports Mod))
      (alloc [_ len]
        ((.. Mod -exports -fress_alloc) byte-length)) ;=> ptr
      (dealloc [Mod ptr len]
        (assert (util/valid-pointer? ptr) (str "dealloc given invalid pointer : '" (pr-str ptr) "'"))
        (assert (and (number? len) (int? len) (<= 0 len)) (str "dealloc given bad length: '" (pr-str len) "'"))
        ((.. Mod -exports -fress_dealloc) ptr len))
      (copy-bytes [this ptr len] ;=> u8-array
        (assert (util/valid-pointer? ptr) (str "copy-bytes given invalid pointer : '" (pr-str ptr) "'"))
        (assert (and (number? len) (int? len) (<= 0 len)) (str "copy-bytes given bad length: '" (pr-str len) "'"))
        (.slice (get-view this) ptr (+ ptr len)))
      (read ;=> object
       ([this ptr] (module-read this ptr nil))
       ([this ptr opts] (module-read this ptr opts)))
      (write ;=> [ptr byte-length]
        ([this any] (module-write this any nil))
        ([this any opts] (module-write this any opts)))
      (write-bytes [this bytes] ;=> [ptr byte-length]
        (module-write-bytes this bytes)))))


; (defn instantiate [arraybuffer importOptions] ;=> promise
;   (let [Mod #js{}]
;
;     ))


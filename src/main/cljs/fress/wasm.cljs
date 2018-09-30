(ns fress.wasm
  (:require [fress.reader :as r]
            [fress.writer :as w]
            [fress.impl.codes :as codes]
            [fress.impl.buffer :as buf]
            [fress.impl.raw-input :as rawIn]
            [fress.util :as util :refer [log]]))

(defprotocol IFressWasmModule
  (get-view [Mod] "return a Uint8Array view over module memory")
  (get-memory [Mod] "the module's memory")
  (get-exports [Mod] "the export object")
  (alloc [Mod len]
    "request a slice of memory of the given length, returning a FatPtr")
  (dealloc
    [Mod fptr]
    [Mod ptr len]
    "return ownership of bytes back to rust for them to be freed")
  (copy-bytes [Mod ptr length] "copy the length bytes from the given address")
  (write-bytes [Mod bytes] "write bytes into memory, returning a FatPtr")
  (read
    [Mod ptr]
    [Mod ptr opts]
    "read an object from a ptr, returning [?err ?ok]")
  (write
    [Mod any]
    [Mod any opts]
    "write an object into memory, returning a FatPtr")
  (read-opts [Mod] "used internally by call")
  (write-opts [Mod] "used internally by call")
  (call
    [Mod export-name]
    [Mod export-name obj]
    "Automatically write-to and read-from a wasm function. See fress.wasm/module-call"))

(deftype FatPtr [ptr len])

(defn- module-read ;=> [?err ?ok]
  "Given a WASM module, a pointer, and opts, read off a fressian object and
   automatically free the used memory. Call this synchronously after
   obtaining the ptr and before any other calls on the same module/memory
     + currently opts is :handlers only
       - no :checksum? or :name->map-ctor (not in rust yet)
   => [?err ?ok]"
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
    ((.. Mod -exports -fress_dealloc) ptr bytes_read)
    ret))

(defonce ^:private _buffer (buf/with-capacity 128))

(defn- module-write ;=> FatPtr
  "Given a WASM module, object, and opts, write the object into wasm memory
   and return a FatPtr containing the recieved ptr and the length of the bytes
   written. The ptr should be given synchronously to an exported wasm function
   to claim and deserialize fressian data.
     + opts
       - :handlers {type write-handler}
       - :stringify-keys bool
       - TODO :record->name, :checksum?
   => FatPtr<pointer, length>"
  [Mod obj {:keys [handlers stringify-keys]}]
  (assert (not (record? obj)) "records are not supported at this time.")
  (let [writer (w/writer _buffer :handlers handlers)
        _ (binding [w/*stringify-keys* (or ^boolean w/*stringify-keys* ^boolean stringify-keys)]
            (w/writeObject writer obj))
        byte-length (buf/getBytesWritten _buffer)
        ptr ((.. Mod -exports -fress_alloc) byte-length)
        view (get-view Mod)]
    (buf/flushTo _buffer view ptr)
    (buf/reset _buffer)
    (FatPtr. ptr byte-length)))

(defn- module-write-bytes ;=> FatPtr
  "Given a WASM module and a byte array, write the bytes into memory and return
   a FatPtr containing the recieved ptr and the length of the bytes written.
   => FatPtr<pointer, length>"
  [Mod bytes]
  (assert (goog.isArrayLike bytes))
  (let [ptr ((.. Mod -exports -fress_alloc) (alength bytes))
        view (get-view Mod)]
    (.set view bytes ptr)
    (FatPtr. ptr (alength bytes))))

;; should we carry state on local module via bindings?
(defonce ^:private _panic_ptr (atom nil))

(def module-call ;=> [err], [nil], [nil ok]
  "Given a WASM module and the string name of an exported wasm function, call
   that function, optionally passing any fressian'able object. This function
   handles all intermediate ptr handling for you.
    + If you pass an object, it will be automatically written into wasm memory
      and the resulting ptr+len handed off to your specified fn. This assumes
      that the exported function's signature accepts *precisely*:
        `your_fn(ptr: *mut u8, len: usize)`
      - All wasm functions that you wish to recieve fressian data should have
        these parameters.
    + if your function returns a ptr it will automatically be read from and
      returned as [?err ?ok].
        `your_fn(ptr: *mut u8, len: usize) -> *mut u8`
    + If no ptr is given it will return as [nil]
    + panics are automatically handled and returned as [{:type :panic, :msg '...'}]
    Note: read and write opts must be passed at module instantiation, otherwise
    this function will become a tangled mess. In the future we may be able to
    generate code which will obviate the need for this fn.
   => [?err ?ok]"
  (let [sentinel (js/Object.)]
   (fn
     ([Mod export-name] (module-call Mod export-name sentinel))
     ([Mod export-name obj]
      (assert (string? export-name))
      (if-let [f (goog.object.get (.-exports Mod) export-name nil)]
        (try
          (if (identical? obj sentinel)
            (if-let [ptr (f)]
              (module-read Mod ptr (read-opts Mod))
              [nil])
            (let [fptr (if (instance? FatPtr obj)
                         obj
                         (module-write Mod obj (write-opts Mod)))]
              (assert (instance? FatPtr fptr))
              (if-let [ptr (f (.-ptr fptr) (.-len fptr))]
                (module-read Mod ptr nil)
                [nil])))
          (catch js/Error e
            (if (.includes (.-name e) "RuntimeError") ;=> wasm runtime-error
              (let [ptr @_panic_ptr
                    _ (reset! _panic_ptr nil)
                    [err panic :as res] (module-read Mod ptr (read-opts Mod))]
                ; (when ^boolean goog.DEBUG (js/console.error e))
                (if err
                  res
                  [{:type :panic :value panic}]))
              (throw e))))
        (throw (js/Error. (str "missing exported fn '" export-name "'"))))))))

(defn attach-protocol! [Mod {:keys [read-opts write-opts]}]
  (let []
    (specify! Mod
      IFressWasmModule
      (get-exports [_] (.-exports Mod))
      (get-memory [_]
        (or (.. Mod -exports -memory) (.. Mod -imports -env -memory)))
      (get-view [this]
        (js/Uint8Array. (.-buffer (get-memory this))))
      (alloc [_ byte-length] ;=> FatPtr
        (let [ptr ((.. Mod -exports -fress_alloc) byte-length)]
          (FatPtr. ptr byte-length)))
      (dealloc
       ([Mod fptr]
        (assert (instance? FatPtr fptr) "fress.wasm/dealloc arity-2 requires a FatPtr")
        ((.. Mod -exports -fress_dealloc) (.-ptr fptr) (.-len fptr)))
       ([Mod ptr len]
         (assert (util/valid-pointer? ptr) (str "dealloc given invalid pointer : '" (pr-str ptr) "'"))
         (assert (and (number? len) (int? len) (<= 0 len)) (str "dealloc given bad length: '" (pr-str len) "'"))
         ((.. Mod -exports -fress_dealloc) ptr len)))
      (copy-bytes [this ptr len] ;=> u8-array
        (assert (util/valid-pointer? ptr) (str "copy-bytes given invalid pointer : '" (pr-str ptr) "'"))
        (assert (and (number? len) (int? len) (<= 0 len)) (str "copy-bytes given bad length: '" (pr-str len) "'"))
        (.slice (get-view this) ptr (+ ptr len)))
      (read ;=> [?err ?ok]
       ([this ptr] (read this ptr nil))
       ([this ptr opts]
        (module-read this (if (instance? FatPtr ptr) (.-ptr ptr) ptr) opts)))
      (write ;=> FatPtr
        ([this any] (module-write this any nil))
        ([this any opts] (module-write this any opts)))
      (write-bytes [this bytes] ;=> FatPtr
        (module-write-bytes this bytes))
      (read-opts [_] read-opts)
      (write-opts [_] write-opts)
      (call ;=> [?err ?ok]
       ([Mod export-name] (module-call Mod export-name))
       ([Mod export-name obj] (module-call Mod export-name obj))))))

(defn assert-fress-mod! [Mod]
  (assert (instance? js/WebAssembly.Instance Mod))
  (assert (some? (.. Mod -exports -fress_init)))
  (assert (some? (.. Mod -exports -fress_alloc)))
  (assert (some? (.. Mod -exports -fress_dealloc)))
  (assert (some? (.. Mod -exports -memory))))

(defn- panic-hook [ptr] (reset! _panic_ptr ptr))

(defn instantiate
  "Instantiate a wasm module and add the IFressWasmModule protocol.
    + opts
      :imports -> Map<(string|kw), fn>
        - this exposes javascript functions to be called by wasm code. For each
          fn, there should be a corresponding externs entry in your rust lib.
          - don't forget that wasm can only call functions with scalar values
      :read-opts
        - passed to read calls internal to IFressWasmModule/call
      :write-opts
        - passed to write calls internal to IFressWasmModule/call

   => native Promise"
  ([array-buffer] (instantiate array-buffer nil)) ; {"fn-name" -> fn}
  ([array-buffer opts]
   (let [base #js{"js_panic_hook" panic-hook}]
     (doseq [[id f] (get opts :imports)]
       (goog.object.set base (name id) f))
     (js/Promise.
      (fn [_resolve reject]
        (.then (js/WebAssembly.instantiate array-buffer #js{"env" base})
               (fn [module]
                 (try
                   (assert-fress-mod! (.-instance module))
                   (let [Mod (attach-protocol! (.-instance module) opts)]
                     ((.. Mod -exports -fress_init))
                     (_resolve Mod))
                   (catch js/Error e
                     (reject e))))
               (fn [reason] (reject reason)))))))) ;=>compile-error
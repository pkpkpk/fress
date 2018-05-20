(ns fress.reader
  (:require [fress.codes :refer [codes]]
            [goog.string :as gstring]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

(defn << [x y]
  (* x (.pow js/Math 2 y)))

(defn read-fully [reader length]
  (log "read-fully length:" length)
  (let [buf (js/Uint8Array. (:buffer @reader) (:index @reader) length)]
    (swap! reader update-in [:index] + length)
    buf))

(def decoder (js/TextDecoder. "UTF-8"))

(defn- internal-read-string [reader length]
  (let [buf (read-fully reader length)]
    (.decode decoder buf)))

(defn read-raw-byte [reader]
  (let [result (some-> (js/Uint8Array. (:buffer @reader) (:index @reader) 1)
                       (aget 0))]
    (log "read-raw-byte result:" result)
    (if (< result 0)
      (throw (js/Error. "EOF"))
      (do
        (swap! reader update-in [:index] inc)
        result))))

(defn read-raw-int8 [reader]
  (read-raw-byte reader))

(defn read-raw-int16 [reader]
  (+ (<< (read-raw-byte reader) 8)
     (read-raw-byte reader)))

(defn read-raw-int24 [reader]
  (+ (<< (read-raw-byte reader) 16)
     (<< (read-raw-byte reader)  8)
     (read-raw-byte reader)))

(defn read-raw-int32 [reader]
  (+ (<< (read-raw-byte reader) 24)
     (<< (read-raw-byte reader) 16)
     (<< (read-raw-byte reader)  8)
     (read-raw-byte reader)))

(defn read-raw-int40 [reader]
  (+ (<< (read-raw-byte reader) 32)
     (read-raw-int32 reader)))

(defn read-raw-int48 [reader]
  (+ (<< (read-raw-byte reader) 40)
     (read-raw-int40 reader)))

(defn read-raw-int64 [reader]
  (+ (<< (read-raw-byte reader) 56)
     (<< (read-raw-byte reader) 48)
     (read-raw-int48 reader)))

(defn- read-next-code [reader]
  (let [code (js/Uint8Array. (:buffer @reader) (:index @reader) 1)]
    (swap! reader update-in [:index] inc)
    (aget code 0)))

(defn internal-read-int [reader]
  (let [code (read-next-code reader)]
    (log "NEXT_CODE:" code)
    (cond
      (= code 0xFF)
      -1

      (<= 0x00 code 0x3F)
      (bit-and code 0xFF)

      (<= 0x40 code 0x5F) ; 64 -> 95
      (+ (<< (- code (codes :int-packed-2-zero)) 8) (read-raw-int8 reader))

      (<= 0x60 code 0x6F)
      (+ (<< (- code (codes :int-packed-3-zero)) 16) (read-raw-int16 reader))

      (<= 0x70 code 0x73)
      (+ (<< (- code (codes :int-packed-4-zero)) 24) (read-raw-int24 reader))

      (<= 0x74 code 0x77)
      (+ (<< (- code (codes :int-packed-5-zero)) 32) (read-raw-int32 reader))

      (<= 0x78 code 0x7B)
      (+ (<< (- code (codes :int-packed-6-zero)) 40) (read-raw-int40 reader))

      (<= 0x7C code 0x7F) (+ (<< (- code (codes :int-packed-7-zero)) 48) (read-raw-int48 reader))

      (= code (codes :int)) (read-raw-int64 reader)

      :default (let [o (read reader code)]
                 (if (= js/Number (type o)) o
                   (throw (js/Error. (str "expected:" "int64" code o))))))))

(defn read-int [reader](internal-read-int reader))
(defn- read-int32 [reader] (read-int reader))
(defn- read-count [reader] (read-int32 reader))

(defn- read [reader code]
  (log "MY READ")
  (cond
    (= code 0xFF) -1
   ; (<= 0x00 code 0x3F) (bit-and code 0xFF)
   ; (<= 0x40 code 0x5F) (+ (<< (- code (codes :int-packed-2-zero)) 8)
   ;                        (read-raw-int8 reader))
   ; (<= 0x60 code 0x6F) (+ (<< (- code (codes :int-packed-3-zero)) 16)
   ;                        (read-raw-int16 reader))
   ; (<= 0x70 code 0x73) (+ (<< (- code (codes :int-packed-4-zero)) 24)
   ;                        (read-raw-int24 reader))
   ; (<= 0x74 code 0x77) (+ (<< (- code (codes :int-packed-5-zero)) 32)
   ;                        (read-raw-int32 reader))
   ; (<= 0x78 code 0x7B) (+ (<< (- code (codes :int-packed-6-zero)) 40)
   ;                        (read-raw-int40 reader))
   ; (<= 0x7C code 0x7F) (+ (<< (- code (codes :int-packed-7-zero)) 48)
   ;                        (read-raw-int48 reader))
   ; (= code (codes :put-priority-cache)) (read-and-cache-object reader)
   ; (= code (codes :get-priority-cache)) (lookup-cache (:priority-cache @reader) (read-int32 reader))
   ;
   ; (<= (codes :priority-cache-packed-start) code (+ (codes :priority-cache-packed-start) 31))
   ; (lookup-cache (:priority-cache @reader) (- code (codes :priority-cache-packed-start)))
   ;
   ; (<= (codes :struct-cache-packed-start) code (+ (codes :struct-cache-packed-start) 15))
   ; (let [st (lookup-cache (:priority-cache @reader) (- code (codes :struct-cache-packed-start)))]
   ;   (handle-struct reader (:tag st) (:fields st)))

   ; (= code (codes :map)) (handle-struct reader "map" 1)
   ; (= code (codes :set)) (handle-struct reader "set" 1)
   ; (= code (codes :uuid)) (handle-struct reader "uuid" 2)
   ; (= code (codes :regex)) (handle-struct reader "regex" 1)
   ; (= code (codes :uri)) (handle-struct reader "uri" 1)
   ; (= code (codes :bigint)) (handle-struct reader "bigint" 1)
   ; (= code (codes :bigdec)) (handle-struct reader "bigdec" 2)
   ; (= code (codes :inst)) (handle-struct reader "inst" 1)
   ; (= code (codes :sym)) (handle-struct reader "sym" 2)
   ; (= code (codes :key)) (handle-struct reader "key" 2)
   ; (= code (codes :int-array)) (handle-struct reader "int[]" 2)
   ; (= code (codes :long-array)) (handle-struct reader "long[]" 2)
   ; (= code (codes :float-array)) (handle-struct reader "float[]" 2)
   ; (= code (codes :boolean-array)) (handle-struct reader "boolean[]" 2)
   ; (= code (codes :double-array)) (handle-struct reader "double[]" 2)
   ; (= code (codes :object-array)) (handle-struct reader "Object[]" 2)

    (<= (codes :bytes-packed-length-start) code (+ (codes :bytes-packed-length-start) 7))
   ; (internal-read-bytes reader (- code (codes :bytes-packed-length-start)))
    (log "inside packed bytes range")

    (= code (codes :bytes))
   ; (internal-read-bytes reader (read-count reader))
    (log "bytes")

    (= code (codes :bytes-chunk))
   ; (internal-read-chunked-bytes reader)
    (log "bytes chunk")

    (<= (codes :string-packed-length-start) code (+ (codes :string-packed-length-start) 7))
   ; (internal-read-string reader (- code (codes :string-packed-length-start)))
    (log "inside packed string range")

    (= code (codes :string))
   ; (internal-read-string reader (read-count reader))
    (let [_(log "CODE = :string")
          length (read-count reader)]
      (internal-read-string reader length))

    (= code (codes :string-chunk))
   ; (internal-read-chunked-string reader (read-count reader))
    (log "string-chunk")

   ; (<= (codes :list-packed-length-start) code (+ (codes :list-packed-length-start) 7))
   ; (internal-read-list reader (- code (codes :list-packed-length-start)))
   ;
   ; (= code (codes :list)) (internal-read-list reader (read-count reader))
   ; (= code (codes :begin-closed-list)) ((core-handlers :list) (read-closed-list reader))
   ; (= code (codes :begin-open-list))   ((core-handlers :list) (read-open-list reader))
   ;
   ; (= code (codes :true)) true
   ; (= code (codes :false)) false
   ;
   ; (some #{(codes :double) (codes :double-0) (codes :double-1)} [code])
   ; (internal-read-double reader code)
   ; (= code (codes :float)) ((core-handlers :float) (read-raw-float reader))
   ; (= code (codes :int)) (read-raw-int64 reader)
   ; (= code (codes :null)) nil
   ;
   ; (= code (codes :footer))
   ; (let [length (dec (reader :index))
   ;       magic-from-stream (+ (bit-shift-left code 24) (read-raw-int24 reader))]
   ;   (validate-footer reader length magic-from-stream)
   ;   (read-object reader))
   ;
   ; (= code (codes :structtype))
   ; (let [tag (read-object reader)
   ;       fields (read-int32 reader)]
   ;   (swap! reader update-in [:struct-cache] conj (StructType. tag fields))
   ;   (handle-struct reader tag fields))
   ;
   ; (= code (codes :struct))
   ; (let [st (lookup-cache (:struct-cache @reader) (read-int32 reader))]
   ;   (handle-struct reader (:tag st) (:fields st)))
   ;
   ; (= code (codes :reset-caches))
   ; (do
   ;   (reset-caches reader)
   ;   (read-object reader))

   :default (throw (js/Error. (str "unmatched code " (pr-str code))
                    ; (expected "any" code)
                    ))))
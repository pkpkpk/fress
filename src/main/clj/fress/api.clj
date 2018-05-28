(ns fress.api
  (:require [clojure.data.fressian :as fressian]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream EOFException]
           [java.nio ByteBuffer]
           [org.fressian.impl RawInput]
           [org.fressian.handlers WriteHandler ReadHandler]
           [org.fressian FressianWriter StreamingWriter FressianReader Writer Reader]
           [org.fressian.impl ByteBufferInputStream BytesOutputStream]))

(defn string->bytes ^bytes [^String s] (.getBytes s "UTF-8"))
(defn bytes->string ^String [bytes] (String. bytes "UTF-8"))

(defn private-field [obj fn-name-string]
  (let [m (.. obj getClass (getDeclaredField fn-name-string))]
    (. m (setAccessible true))
    (. m (get obj))))

(defn w->raw [wrt] (private-field wrt "rawOut"))
(defn rdr->raw [rdr] (private-field rdr "is"))

(deftype utf8 [s])

(def utf8-writer
  (reify WriteHandler
    (write [_ w u]
      (let [bytes (.getBytes (.-s u) "UTF-8")
            raw-out (w->raw w)
            length (count bytes)]
        (.writeTag w "utf8" 2)
        (.writeCount w length)
        (.writeRawBytes raw-out bytes 0 length)))))

(def utf8-reader
  (reify ReadHandler
    (read [_ rdr tag component-count]
      (let [length (int (.readInt rdr))
            offset (int 0)
            byteBuffer (byte-array length)
            raw-in (rdr->raw rdr)]
        (.readFully raw-in byteBuffer offset length)
        (bytes->string byteBuffer)))))

(def write-handlers
  (-> (merge {utf8 {"utf8" utf8-writer}} fressian/clojure-write-handlers)
      fressian/associative-lookup
      fressian/inheritance-lookup))

(def read-handlers
  (-> (merge {"utf8" utf8-reader} fressian/clojure-read-handlers)
      fressian/associative-lookup))

(defn utf8-test []
  (let [baos (ByteArrayOutputStream.)
        writer (fressian/create-writer baos :handlers write-handlers)
        s "😉 😎 🤔 😐 🙄"
        u (utf8. s)]
    (.writeAs writer "utf8" u)
    (let [bais (ByteArrayInputStream. (.toByteArray baos))
          reader (fressian/create-reader bais :handlers read-handlers)
          out (fressian/read-object reader)]
      (assert (= s out)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; from fressian/src/test/org/fressian/api.clj

(defn ^ByteBuffer bytestream->buf
  "Return a readable buf over the current internal state of a
   BytesOutputStream."
  [^BytesOutputStream stream]
  (ByteBuffer/wrap (.internalBuffer stream) 0 (.length stream)))

(defn byte-buffer-seq
  "Return a lazy seq over the remaining bytes in the buffer.
   Not fast: intented for REPL usage.
   Works with its own duplicate of the buffer."
  [^ByteBuffer bb]
  (lazy-seq
   (when (.hasRemaining bb)
     (let [next-slice (.slice bb)]
       (cons (.get next-slice) (byte-buffer-seq next-slice))))))

(defn ^ByteBuffer byte-buf
  "Return a byte buffer with the fressianed form of object.
   See fressian for options."
  [obj & {:keys [handlers footer]}]
  (let [BYTES-os (BytesOutputStream.)
        writer (fressian/create-writer BYTES-os :handlers write-handlers)]
    (.writeObject writer obj)
    (bytestream->buf BYTES-os)))

(defn read-batch
  "Read a fressian reader fully (until eof), returning a (possibly empty)
   vector of results."
  [^Reader fin]
  (let [sentinel (Object.)]
    (loop [objects []]
      (let [obj (try (.readObject fin) (catch EOFException e sentinel))]
        (if (= obj sentinel)
          objects
          (recur (conj objects obj)))))))

(extend ByteBuffer
  io/IOFactory
  (assoc io/default-streams-impl
    :make-input-stream (fn [x opts] (io/make-input-stream
                                     (ByteBufferInputStream. x) opts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn byteseq [obj] (byte-buffer-seq (byte-buf obj)))

(defn bytes->rdr
  [bytes]
  (let [in (if (= (type bytes) java.nio.HeapByteBuffer)
             bytes
             (byte-array bytes))]
    (fressian/create-reader (io/input-stream in) :handler read-handlers)))

(defn ->rdr [o] (-> o byte-buf bytes->rdr))
(defn ->raw [o] (-> o byte-buf bytes->rdr rdr->raw))

(defn raw->rawbytes [raw]
  (loop [acc []]
    (let [byte (try
                 (.readRawByte raw)
                 (catch java.io.IOException _ false))]
      (if-not byte
        acc
        (recur (conj acc byte))))))

(defn rawbyteseq [obj]
  (let [bytes (byte-buf obj)
        rdr (bytes->rdr bytes)
        raw (rdr->raw rdr)]
    (raw->rawbytes raw)))

(defmacro sample [form]
  (let [value (eval form)
        bytes (byteseq value)
        rawbytes (rawbyteseq value)]
    ; [(pr-str form) value (vec bytes) (vec rawbytes)]
    (cond-> {:form (pr-str form)
             :bytes (vec bytes)
             :rawbytes (vec rawbytes)}
      (not (bytes? value)) (assoc :value value))))

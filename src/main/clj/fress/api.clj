(ns fress.api
  (:require [clojure.data.fressian :as fressian]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream ByteArrayInputStream EOFException]
           [java.nio ByteBuffer]
           [org.fressian.impl RawInput Codes]
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

(defn utf8? [o] (instance? utf8 o))

(def utf8-writer
  (reify WriteHandler
    (write [_ w u]
      (let [bytes (.getBytes (.-s u) "UTF-8")
            raw-out (w->raw w)
            length (count bytes)]
        ; (.writeTag w "utf8" 2)
        (.writeCode w (int 191)) ;<= needs to be picked up by client
        (.writeCount w length)
        (.writeRawBytes raw-out bytes 0 length)))))

(def utf8-reader
  ;; cant modify fressian.impl.Codes so using code from client will fail
  ;; will not recognized without "utf8" tag, see writer
  ;; client will need to write with tag when targeting JVM
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
    (when footer (.writeFooter writer))
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

(defn byteseq [obj & {:keys [handlers footer]}]
  (byte-buffer-seq (byte-buf obj :footer footer)))

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

(defn rawbyteseq [obj & {:keys [handlers footer]}]
  (let [bytes (byte-buf obj :footer footer)
        rdr (bytes->rdr bytes)
        raw (rdr->raw rdr)]
    (raw->rawbytes raw)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;js MAX_SAFE_INT etc


(defn typed-array? [o]
  (let [c (class o)]
    (or
     (= c (Class/forName "[B"))
     (= c (Class/forName "[S"))
     (= c (Class/forName "[I"))
     (= c (Class/forName "[J")) ;=> LONG_ARRAY
     (= c (Class/forName "[F"))
     (= c (Class/forName "[D"))
     (= c (Class/forName "[Ljava.lang.Object;")))))

(defmacro sample
  ([form & {:keys [footer] :or {footer false}}]
   (let [value (eval form)
         bytes (byteseq value :footer footer)
         rawbytes (rawbyteseq value :footer footer)
         value (if (utf8? value) (.-s value)  value)
         base {:form (pr-str form)
               :bytes (vec bytes)
               :footer footer
               :rawbytes (vec rawbytes)}]
     (if-not (or (typed-array? value) (bytes? value))
       (assoc base :value value)
       (assoc base :input (eval (second form)))))))


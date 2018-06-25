(ns fress.samples
  (:require [fress.api :refer [write-handlers read-handlers] :as api]
            [clojure.data.fressian :as fressian]
            [clojure.java.io :as io])
  (:import [fress.api utf8]
           [java.io ByteArrayOutputStream ByteArrayInputStream EOFException]
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
        write-handlers (or handlers write-handlers)
        writer (fressian/create-writer BYTES-os :handlers write-handlers)]
    (.writeObject writer obj)
    (when footer (.writeFooter writer))
    (bytestream->buf BYTES-os)))

(extend ByteBuffer
  io/IOFactory
  (assoc io/default-streams-impl
    :make-input-stream (fn [x opts] (io/make-input-stream
                                     (ByteBufferInputStream. x) opts))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn bytevec [obj & {:keys [handlers footer]}]
  (vec (byte-buffer-seq (byte-buf obj :handlers handlers :footer footer))))

(defn bytes->rdr
  [bytes]
  (let [in (if (= (type bytes) java.nio.HeapByteBuffer)
             bytes
             (byte-array bytes))]
    (fressian/create-reader (io/input-stream in) :handler read-handlers)))

(defn ->rdr [o] (-> o byte-buf bytes->rdr))
(defn ->raw-in [o] (-> o byte-buf bytes->rdr rdr->raw))

(defn raw-in->ubytes [raw]
  (loop [acc []]
    (let [byte (try
                 (.readRawByte raw)
                 (catch java.io.IOException _ false))]
      (if-not byte
        acc
        (recur (conj acc byte))))))

(defn ubytevec [obj & {:keys [handlers footer]}]
  (let [bytes (byte-buf obj :footer footer)
        rdr (bytes->rdr bytes)
        raw (rdr->raw rdr)]
    (raw-in->ubytes raw)))

(defn typed-array? [o]
  (let [c (class o)]
    (or
     (= c (Class/forName "[Z")) ;=> BOOLEAN_ARRAY
     (= c (Class/forName "[B"))
     (= c (Class/forName "[S"))
     (= c (Class/forName "[I"))
     (= c (Class/forName "[J")) ;=> LONG_ARRAY
     (= c (Class/forName "[F"))
     (= c (Class/forName "[D"))
     (= c (Class/forName "[Ljava.lang.Object;")))))

(defmacro sample
  ([form & {:keys [footer tag-utf8] :or {footer false}}]
   (binding [api/*write-utf8-tag* tag-utf8]
     (let [value (if (symbol? form) form (eval form))
           bytes (mapv long (bytevec value :footer footer))
           ; ubytes (mapv long (ubytevec value :footer footer))
           base {:form (pr-str form)
                 :bytes bytes
                 :byte-count (count bytes)
                 :footer footer
                 ; :ubytes ubytes
                 ; :ubyte-count (count ubytes)
                 }]
       (cond
         (or (typed-array? value) (bytes? value))
         (assoc base :input (eval (second form)))

         (api/utf8? value) ;need tree-seq with value transform for nested
         (assoc base :tag? api/*write-utf8-tag* :value (.-s value))

         (symbol? value)
         `(assoc ~base
                 :value '(quote ~value)
                 :form (quote ~form) #_(str "'" (name (quote ~value))))

         (instance? java.net.URI value)
         (assoc base :input (.toString value))

         :else
         (assoc base :value value))))))

(defmacro sample-each [coll] (vec (for [item coll]  `(sample ~item))))

(defmacro uri-samples [coll]
  (vec (for [item coll]  `(sample (java.net.URI. ~item)))))

(deftype Person [ ^String firstName ^String  lastName]
  Object
  (toString [this] (str "Person " firstName " " lastName)))

(defn write-person []
  (let [BYTES-os (BytesOutputStream.)
        tag "org.fressian.Examples.Person"
        person-writer (reify WriteHandler
                        (write [_ w person]
                          (println tag)
                          (.writeTag w tag 2)
                          (.writeObject w (.-firstName person))
                          (.writeObject w (.-lastName person))))
        handlers (-> (merge {Person {tag person-writer}} fressian/clojure-write-handlers)
                     fressian/associative-lookup
                     fressian/inheritance-lookup)
        writer (fressian/create-writer BYTES-os :handlers handlers)]
    (.writeObject writer (->Person "jonny" "greenwood")) ;<= triggers struct caching
    (.writeObject writer (->Person "thom" "yorke")) ;<= written with cache reference code
    (bytestream->buf BYTES-os)))

(def bunchOfData #{1 false "hello" (java.util.Date.) -42})

(defn write-cached []
  (let [BYTES-os (BytesOutputStream.)
        handlers (-> fressian/clojure-write-handlers
                     fressian/associative-lookup
                     fressian/inheritance-lookup)
        writer (fressian/create-writer BYTES-os :handlers handlers)]
    (.writeObject writer bunchOfData true) ;<= triggers priorityCache
    (.writeObject writer bunchOfData true) ;<= written with cache reference code
    (bytestream->buf BYTES-os)))

(defrecord Book [author title])

(defn class-sym
  "Returns the class name of inst as a symbol."
  [^Object inst]
  (-> inst (.getClass) (.getName) symbol))

(defn record-sample []
  (let [book (->Book "Borges" "El jardín de senderos que se bifurcan")]
    {:bytes (bytevec book)
     :author (:author book)
     :title (:title book)
     :class-sym (class-sym book)}))

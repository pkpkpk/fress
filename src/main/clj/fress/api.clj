(ns fress.api
  (:require [clojure.data.fressian :as fressian])
  (:import [org.fressian.impl RawInput]
           [java.io ByteArrayOutputStream ByteArrayInputStream]
           [org.fressian.handlers WriteHandler ReadHandler]))

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

(def my-read-handlers
  (-> (merge {"utf8" utf8-reader} fressian/clojure-read-handlers)
      fressian/associative-lookup))

(defn utf8-test []
  (let [baos (ByteArrayOutputStream.)
        writer (fressian/create-writer baos :handlers write-handlers)
        s "😉 😎 🤔 😐 🙄"
        u (utf8. s)]
    (.writeAs writer "utf8" u)
    (let [bais (ByteArrayInputStream. (.toByteArray baos))
          reader (fressian/create-reader bais :handlers my-read-handlers)
          out (fressian/read-object reader)]
      (assert (= s out)))))

(ns fress.api
  (:require [clojure.data.fressian :as fressian]
            [clojure.java.io :as io])
  (:import [org.fressian.handlers WriteHandler ReadHandler]))

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

(def ^:dynamic *write-utf8-tag* false)

(def utf8-writer
  (reify WriteHandler
    (write [_ w u]
      (let [bytes (.getBytes (.-s u) "UTF-8")
            raw-out (w->raw w)
            length (count bytes)]
        (if *write-utf8-tag* ;<= client can read either
          (.writeTag w "utf8" 2)
          (.writeCode w (int 191)))
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
            bytes (byte-array length)
            raw-in (rdr->raw rdr)]
        (.readFully raw-in bytes offset length)
        (bytes->string bytes)))))

(def write-handlers
  (-> (merge {utf8 {"utf8" utf8-writer}} fressian/clojure-write-handlers)
      fressian/associative-lookup
      fressian/inheritance-lookup))

(def read-handlers
  (-> (merge {"utf8" utf8-reader} fressian/clojure-read-handlers)
      fressian/associative-lookup))

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


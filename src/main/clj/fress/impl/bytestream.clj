(ns fress.impl.bytestream
  (:import java.nio.ByteBuffer
           org.fressian.impl.BytesOutputStream)
  (:gen-class
     :implements [clojure.lang.IDeref]
     :extends org.fressian.impl.BytesOutputStream))

(defn -deref [this]
  (ByteBuffer/wrap (.internalBuffer this) 0 (.length this)))
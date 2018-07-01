(ns fress.test-helpers
  (:require [clojure.test :refer [deftest is]]
            [fress.api :as fress])
  (:import
   [java.io InputStream OutputStream EOFException]
   java.nio.ByteBuffer
   java.nio.charset.Charset
   [org.fressian FressianWriter StreamingWriter FressianReader Writer Reader]
   [org.fressian.handlers WriteHandler ReadHandler ILookup  WriteHandlerLookup]
   [org.fressian.impl ByteBufferInputStream BytesOutputStream]))

(defn byte-buffer-seq
  "Return a lazy seq over the remaining bytes in the buffer.
   Not fast: intented for REPL usage.
   Works with its own duplicate of the buffer."
  [^ByteBuffer bb]
  (lazy-seq
   (when (.hasRemaining bb)
     (let [next-slice (.slice bb)]
       (cons (.get next-slice) (byte-buffer-seq next-slice))))))

; (defn bytevec [bytes]
;   (into [] (byte-buffer-seq bytes)))

(defn stream->bytevec [bytestream]
  (into [] (byte-buffer-seq (fress/bytestream->buf bytestream))))
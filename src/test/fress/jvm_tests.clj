(ns fress.jvm-tests
  (:require [clojure.test :refer [deftest is testing]]
            [fress.api :as fress]
            [fress.test-helpers :refer [stream->bytevec]])
  (:import
   [java.io InputStream OutputStream EOFException]
   java.nio.ByteBuffer
   java.nio.charset.Charset
   [org.fressian FressianWriter StreamingWriter FressianReader Writer Reader]
   [org.fressian.handlers WriteHandler ReadHandler ILookup  WriteHandlerLookup]
   [org.fressian.impl ByteBufferInputStream BytesOutputStream]))

(deftest cache-arity-test
  (let [data ["soomme string" "annother" #{"some more" "strings!!"}]
        uncached-out (fress/byte-stream)
        writer (fress/create-writer uncached-out)]
    (fress/write-object writer data)
    (fress/write-object writer data)
    (fress/write-object writer data)
    (let [uncached-bytes (stream->bytevec uncached-out)
          cached-out (fress/byte-stream)
          writer (fress/create-writer cached-out)]
      (fress/write-object writer data true)
      (fress/write-object writer data true)
      (fress/write-object writer data true)
      (let [cached-bytes (stream->bytevec cached-out)]
        (is (< (count cached-bytes) (count uncached-bytes)))))))

(deftest read+write-utf8
  (binding [fress/*write-utf8-tag* true]
    (let [s "hello"
          bs (fress/write (fress/->utf8 s))]
      (is (= (fress/read bs) s)))
    (testing  "coerce BytesOutputStream to input stream"
      (let [s "hello"
            bs (fress/byte-stream)
            writer (fress/create-writer bs)
            _(fress/write-utf8 writer s)
            rdr (fress/create-reader  bs)]
        (is (=  s (fress/read-object rdr)))))
    (testing "coerce ByteBuffer to input stream"
      (let [s "hello"
            bs (fress/byte-stream)
            writer (fress/create-writer bs)
            _(fress/write-utf8 writer s)
            rdr (fress/create-reader (fress/byte-stream->buf bs))]
        (is (=  s (fress/read-object rdr)))))
    (testing "proper input-stream should still be ok"
      (let [s "hello"
            out (fress/byte-stream)
            writer (fress/create-writer out)
            _(fress/write-utf8 writer s)
            in (clojure.data.fressian/to-input-stream out)
            rdr (fress/create-reader in)]
        (is (=  s (fress/read-object rdr)))))))


(defrecord SomeRec [f0])

(deftest field-caching-writer-test
  (let [no-cache (fress/byte-stream)
        wrt (fress/create-writer no-cache)]
    (fress/write-object wrt (SomeRec. "foobar"))
    (fress/write-object wrt (SomeRec. "foobar"))
    (fress/write-object wrt (SomeRec. "foobar"))
    (let [cached (fress/byte-stream)
          cache-writer (fress/field-caching-writer #{:f0})
          wrt (fress/create-writer cached
                                 :handlers {clojure.lang.IRecord
                                            {"clojure/record" cache-writer}})]
      (fress/write-object wrt (SomeRec. "foobar"))
      (fress/write-object wrt (SomeRec. "foobar"))
      (fress/write-object wrt (SomeRec. "foobar"))
      (is (<  (count (stream->bytevec cached)) (count (stream->bytevec no-cache))))
      (let [no-cache-rdr (fress/create-reader no-cache)
            no-cache-val (fress/read-batch no-cache-rdr)
            cached-rdr (fress/create-reader cached)
            cached-val (fress/read-batch cached-rdr)]
        (is (= no-cache-val cached-val))
        (is (every? #(instance? SomeRec %) (concat cached-val no-cache-val)))))))

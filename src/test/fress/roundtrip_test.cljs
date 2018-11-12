(ns fress.roundtrip-test
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [cljs.tools.reader :refer [read-string]]
            [fress.impl.raw-input :as rawIn]
            [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.reader :as r]
            [fress.writer :as w]
            [fress.api :as api]
            [fress.impl.buffer :as buf]
            [fress.samples :as samples]
            [fress.util :refer [byte-array] :as util]
            [fress.test-helpers :as helpers :refer [log is= seq= are-nums= float=]]))

(deftest int-test
  (doseq [{:keys [form bytes value throw?]} samples/int-samples]
    (testing form
      (let [out (byte-array (count bytes))
            wrt (w/writer out)
            rdr (r/reader out)]
        (if-not throw?
          (do
            (testing (str "writing " form)
              (w/writeObject wrt value)
              (are-nums= bytes out))
            (testing (str "reading " form)
              (is= value (r/readObject rdr))))
          (testing "unsafe ints"
            (testing "writing unsafe int"
              (is (thrown? js/Error (w/writeObject wrt value))))
            (testing "reading unsafe int"
              (is (thrown? js/Error (r/readObject (r/reader (byte-array bytes))))))))))))

(deftest float-test
  (doseq [{:keys [form bytes value throw?]} (concat samples/float-samples samples/double-samples)]
    (testing form
      (let [out (byte-array (count bytes))
            wrt (w/writer out)
            rdr (r/reader out)]
        (w/writeObject wrt value)
        (are-nums= bytes out)
        (is (float= value (r/readObject rdr)))))))

(deftest string-test
  (doseq [{:keys [form bytes value chunked?]} (samples/string-samples)]
    (testing form
      (let [out (byte-array (count bytes))
            wrt (w/writer out)
            rdr (r/reader out)]
        (w/writeObject wrt value)
        (are-nums= bytes out)
        (is value (r/readObject rdr))
        (testing "read by component"
          (let [rdr (r/reader out)
                bytelength (alength (.encode util/TextEncoder value))]
            (cond
              chunked?
              (do
                (is= (r/readNextCode rdr) codes/STRING_CHUNK)
                (is= (r/readCount- rdr) (inc util/U16_MAX_VALUE)))

              (< (alength out) 8)
              (is= (r/readNextCode rdr) (+ codes/STRING_PACKED_LENGTH_START bytelength))

              :else
              (is= (r/readNextCode rdr) codes/STRING))))))))

(deftest bytes-test
  (doseq [{:keys [form bytes input chunked?]} (samples/byte-samples)]
    (testing form
      (let [out (byte-array (count bytes))
            wrt (w/writer out)
            rdr (r/reader out)
            value (byte-array input)]
        (w/writeObject wrt value)
        (are-nums= bytes out)
        (is value (r/readObject rdr))
        (testing "read by component"
          (let [rdr (r/reader out)]
            (cond
              chunked?
              (do
                (is= (r/readNextCode rdr) codes/BYTES_CHUNK)
                (is= (r/readCount- rdr) ranges/BYTE_CHUNK_SIZE))

              (<= (alength out) 8)
              (is= (r/readNextCode rdr) (+ codes/BYTES_PACKED_LENGTH_START (count input)))

              :else
              (do
                (is= (r/readNextCode rdr) codes/BYTES)
                (is= (r/readNextCode rdr) (count input))))))))))

(deftest rawUTF8-test
  (doseq [{:keys [form bytes value tag?]} samples/utf8-samples]
    (testing form
      (let [out (byte-array (count bytes))
            wrt (w/writer out)
            rdr (r/reader out)]
        (binding [w/*write-raw-utf8* true
                  w/*write-utf8-tag* tag?]
          (w/writeObject wrt value))
        (are-nums= bytes out)
        (is value (r/readObject rdr))))))

(deftest misc-roundtrip
  (doseq [{:keys [form bytes value]} samples/misc-samples]
    (testing form
      (let [out (byte-array (count bytes))
            wrt (w/writer out)
            rdr (r/reader out)]
        (w/writeObject wrt value)
        (are-nums= bytes out)
        (is= value (r/readObject rdr))))))

(deftest uri-roundtrip
  (doseq [{:keys [form bytes input]} samples/uri-samples]
    (testing form
      (let [out (byte-array (count bytes))
            wrt (w/writer out)
            rdr (r/reader out)
            value (goog.Uri. input)]
        (w/writeObject wrt value)
        (are-nums= bytes out)
        (is= (.toString value) (.toString (r/readObject rdr)))))))


(def typed-array-sym->writer
  {'long-array   w/writeLongArray
   'object-array w/writeObjectArray
   'boolean-array w/writeBooleanArray})

(deftest typed-array-test
  (doseq [{:keys [form bytes value input byte-count]} samples/typed-array-samples]
    (testing form
      (let [out (byte-array (or byte-count (count bytes)))
            wrt (w/writer out)
            rdr (r/reader out)
            sym (first (read-string form))
            value ((helpers/typed-array-sym->fn sym) input)]
        (if-let [bypass-writer (typed-array-sym->writer sym)]
          (bypass-writer wrt value)
          (w/writeObject wrt value))
        (are-nums= bytes out)
        (is (seq= value (r/readObject rdr)))))))

(deftest footer-roundtrip
  (doseq [{:keys [form bytes value input]} samples/footer-samples]
    (testing form
      (let [out (byte-array (count bytes))
            wrt (w/writer out)
            rdr (r/reader out)
            value (or value (byte-array input))]
        (w/writeObject wrt value)
        (w/writeFooter wrt)
        (are-nums= bytes out)
        (is (seq= value (r/readObject rdr)))
        (is (nil? (r/validateFooter rdr)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftype Person [firstName lastName]
  IEquiv
  (-equiv [this that]
    (and (= (type this) (type that))
         (= firstName (.-firstName that))
         (= lastName (.-lastName that)))))

(defn writePerson
  [wrt person]
  (w/writeTag wrt "org.fressian.Examples.Person" 2)
  (w/writeObject wrt (.-firstName person))
  (w/writeObject wrt (.-lastName person)))

(defn readPerson [rdr tag fields]
  (Person. (r/readObject rdr) (r/readObject rdr)))

(deftest struct+cache-test
  (let [bytes [-17 -29 28 111 114 103 46 102 114 101 115 115 105 97 110 46 69 120 97 109 112 108 101 115 46 80 101 114 115 111 110 2 -33 106 111 110 110 121 -29 9 103 114 101 101 110 119 111 111 100 -96 -34 116 104 111 109 -33 121 111 114 107 101]
        tag "org.fressian.Examples.Person"
        out (byte-array (count bytes))
        ; wrt (w/writer out :handlers {Person writePerson})
        wrt (api/create-writer out :handlers {Person writePerson})
        jonny (->Person "jonny" "greenwood")
        thom (->Person "thom" "yorke")]
    (w/writeObject wrt jonny) ;<= triggers struct caching
    (w/writeObject wrt thom)
    (are-nums= bytes out)
    (testing "read by component"
      (let [rdr (r/reader out)]
        (is= (r/readNextCode rdr) codes/STRUCTTYPE)
        (is= (r/readObject rdr) tag)
        (is= (r/readObject rdr) 2)
        (is= (r/readObject rdr) "jonny")
        (is= (r/readObject rdr) "greenwood")
        ;; writer has now cached the object..., reader keeps record
        ;; at cache index 0
        (is= (r/readNextCode rdr) (+ codes/STRUCT_CACHE_PACKED_START 0))
        (is= (r/readObject rdr) "thom")
        (is= (r/readObject rdr) "yorke")))
    (testing "without handler => tagged-object"
      (let [rdr (r/reader out)
            o1 (r/readObject rdr)
            o2 (r/readObject rdr)]
        (is (and (instance? r/TaggedObject o1)
                 (= (.-tag o1) tag)
                 (= (vec (.-value o1)) ["jonny" "greenwood"])))
        (is (and (instance? r/TaggedObject o2)
                 (= (.-tag o2) tag)
                 (= (vec (.-value o2)) ["thom" "yorke"])))))
    (testing "with handler"
      (let [rdr (r/reader out :handlers {tag readPerson})]
        (is= (r/readObject rdr) jonny)
        (is= (r/readObject rdr) thom)))))
;
; ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
; ;; records
;
(defrecord Book [author title])

(deftest record-test
  (let [{:keys [bytes author title class-sym]} samples/record-sample
        out (byte-array (count bytes))
        wrt (api/create-writer out :record->name {Book "fress.api.Book"})
        value (Book. author title)]
    (api/write-object wrt value)
    (are-nums= bytes out)
    (testing "no reader => tagged-object"
      (let [rdr (api/create-reader out)
            o (api/read-object rdr)]
        (is (api/tagged-object? o))
        (is= (api/tag o) "record")
        (let [v (api/tagged-value o)]
          (is (aget v 0) 'fress.api.Book)
          (is (js->clj (aget v 1)) {:author author :title title}))))
    (testing "add defrecord ctor  => record instance"
      (let [rdr (api/create-reader out :name->map-ctor {"fress.api.Book" map->Book})
            o (api/read-object rdr)]
        (is (instance? Book o))
        (is= o (Book. author title))))))

;; field caching writer ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord SomeRec [f0])

(deftest field-caching-writer-test
  (let [no-cache (api/byte-stream)
        wrt (api/create-writer no-cache :record->name {SomeRec "SomeRec"})]
    (api/write-object wrt (SomeRec. "foobar"))
    (api/write-object wrt (SomeRec. "foobar"))
    (api/write-object wrt (SomeRec. "foobar"))
    (let [cached (api/byte-stream)
          cache-writer (api/field-caching-writer #{:f0})
          wrt (api/create-writer cached
                                 :handlers {"record" cache-writer}
                                 :record->name {SomeRec "SomeRec"})]
      (api/write-object wrt (SomeRec. "foobar"))
      (api/write-object wrt (SomeRec. "foobar"))
      (api/write-object wrt (SomeRec. "foobar"))
      (is (<  (alength @cached) (alength @no-cache)))
      (let [name->map-ctor {"SomeRec" map->SomeRec}
            no-cache-rdr (api/create-reader @no-cache :name->map-ctor name->map-ctor)
            no-cache-val (api/read-batch no-cache-rdr)
            cached-rdr (api/create-reader @cached :name->map-ctor name->map-ctor)
            cached-val (api/read-batch cached-rdr)]
        (is (= no-cache-val cached-val))
        (is (every? #(instance? SomeRec %) (concat cached-val no-cache-val)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; caching
(deftest cached-test
  (let [{:keys [value bytes]} samples/cached-sample
        out (byte-array (count bytes))
        wrt (w/writer out)
        rdr (r/reader out)]
    (w/writeObject wrt value true)
    (w/writeObject wrt value true)
    (are-nums= bytes out)
    (is= (r/readObject rdr) value)
    (is= (r/readObject rdr) value)
    (testing "read by component"
      (let [rdr (r/reader out)]
        (is= (r/readNextCode rdr) codes/PUT_PRIORITY_CACHE)
        (is= (r/readObject rdr) value)
        (is= (r/readNextCode rdr) (+ codes/PRIORITY_CACHE_PACKED_START 0))
        (is (thrown-with-msg? js/Error #"EOF" (r/readObject rdr)))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; error example

(deftype Err [name message stack])

(defn write-error [writer err]
  (let [name (.-name err)
        msg (.-message err)
        stack "elided"] ;(.-stack err)
    (w/writeTag writer "js-error" 3) ;<-- don't forget field count!
    (w/writeObject writer name)
    (w/writeObject writer msg)
    (w/writeObject writer stack)))

(defn read-error [reader tag field-count]
  {:name (r/readObject reader)
   :msg (r/readObject reader)
   :stack (r/readObject reader)})

(deftest extend-error-test
  (let [tag "js-error"
        e (js/Error "a generic error")
        te (js/TypeError "a type error")
        ce (Err. "a name!" "a msg!" "c")]
    (testing "missing write handler"
      (let [out (api/byte-stream)
            wrt (w/writer out)]
        (is (thrown? js/Error (w/writeObject wrt e)))))
    (testing "exact match + inheritance-lookup"
      (let [out (api/byte-stream)
            wrt (w/writer out :handlers {js/Error write-error})]
        (is (nil? (w/writeObject wrt e)))
        (is (nil? (w/writeObject wrt te)) "type error should work via inheritance")
        (is (thrown? js/Error (w/writeObject wrt ce)))))
    (testing "overload write fn"
      (let [out (api/byte-stream)
            wrt (w/writer out :handlers {[js/Error Err] write-error})]
        (is (nil? (w/writeObject wrt e)))
        (is (nil? (w/writeObject wrt te)))
        (is (nil? (w/writeObject wrt ce)))
        (testing "no read fn"
          (let [rdr (api/create-reader out)
                [a b c :as tos] (api/read-all rdr)]
            (is (every? api/tagged-object? tos))
            (is (= ["js-error" "js-error" "js-error"] (map :tag tos)))
            (is (= (vec (get a :value)) ["Error" "a generic error" "elided"]))
            (is (= (vec (get b :value)) ["TypeError" "a type error" "elided"]))
            (is (= (vec (get c :value)) ["a name!" "a msg!" "elided"]))))
        (testing "with read fn"
          (let [rdr (api/create-reader out :handlers {tag read-error})]
            (let [{:keys [name msg]} (r/readObject rdr)]
              (is= name "Error")
              (is= msg "a generic error"))
            (let [{:keys [name msg]} (r/readObject rdr)]
              (is= name "TypeError")
              (is= msg "a type error"))
            (let [{:keys [name msg]} (r/readObject rdr)]
              (is= name "a name!")
              (is= msg "a msg!"))))))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defrecord baz [x y]) ; 120 121

(deftest circular-caching-test
  (let [baz1 (baz. 2 3)
        baz2 (baz. 4 5)
        foobar [baz1 baz2]
        buffer (fress.api/byte-stream)
        writer (fress.api/create-writer buffer :record->name {baz "baz"})
        _(api/write-object writer foobar)
        reader (api/create-reader buffer :name->map-ctor {"baz" map->baz})]
    (is= foobar (fress.api/read-object reader))))


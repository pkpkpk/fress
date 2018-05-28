(ns fress.reader
  (:require-macros [fress.macros :refer [<< >>>]])
  (:require [fress.impl.raw-input :as rawIn]
            [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.util :refer [expected byte-array]])
  (:import [goog.math Long]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

(def ^:const I32_MAX_VALUE 2147483647)
(def ^:const I32_MIN_VALUE -2147483648)

(defrecord StructType [tag fields])
(defrecord TaggedObject [tag value]) ;meta

(defn read-utf8-chars [source offset length]
  (let [buf (js/Array.)]
    (loop [pos 0]
      (let [ch (bit-and (aget source pos) 0xff)
            ch>>4 (bit-shift-right ch 4)]
        (when (< pos length)
          (cond
           (<=  0 ch>>4 7) (do (.push buf ch) (recur (inc pos)))
           (<= 12 ch>>4 13) (let [ch1 (aget source (inc pos))]
                           (.push buf (bit-or
                                       (bit-shift-left
                                        (bit-and ch 0x1f) 6)
                                       (bit-and ch1 0x3f)))
                           (recur (+ pos 2)))
           (= ch>>4 14) (let [ch1 (aget source (inc pos))
                           ch2 (aget source (+ pos 2))]
                       (.push buf (bit-or
                                   (bit-shift-left
                                    (bit-and ch 0x0f) 12)
                                   (bit-shift-left
                                    (bit-and ch1 0x03f) 6)
                                   (bit-and ch2 0x3f)))
                       (recur (+ pos 3)))
           :default (throw (str "Invalid UTF-8: " ch))))))
    (.apply (.-fromCharCode js/String) nil buf)))

(def TextDecoder (js/TextDecoder. "utf8"))

(defn readUTF8
  "this uses TextDecoder on raw utf8 bytes instead of using js on compressed
   fressian string bytes"
  [rdr] ;tag fields
  (let [length (readCount- rdr)
        bytes (rawIn/readFully (:raw-in rdr) length)]
    (.decode TextDecoder bytes)))

(defprotocol IFressianReader
  (read- [this code])
  (readNextCode [this])
  (readBoolean [this])
  (readInt [this])
  (readDouble [this])
  (readFloat [this])
  (readInt32 [this])
  (readObject [this])
  (readCount- [this])
  (readObjects- [this length])
  (readClosedList- [this])
  (readOpenList- [this])
  (readAndCacheObject- [this cache])
  (lookupCache- [this cache index])
  (validateFooter- [this] [this calculatedLength magicFromStream])
  (handleStruct- [this ^string tag fields])
  (getHandler- [this ^string tag])
  (getPriorityCache- [this])
  (getStructCache- [this])
  (resetCaches- [this]))

(defn ^number internalReadDouble [rdr code]
  (cond
    (== code codes/DOUBLE)
    (rawIn/readRawDouble (.-raw-in rdr))
    (== code codes/DOUBLE_0)
    0.0
    (== code codes/DOUBLE_1)
    1.0
    :else
    (let [o (read- rdr code)]
      (if (number? o)
        o
        (expected rdr "double" code o)))))

(defn ^number internalReadInt
  ([rdr](internalReadInt rdr (readNextCode rdr) ))
  ([rdr code]
   (cond
     (== code 0xFF) -1

     (<= 0x00 code 0x3F)
     (bit-and code 0xFF)

     (<= 0x40 code 0x5F)
     (bit-or (<< (- code codes/INT_PACKED_2_ZERO) 8) (rawIn/readRawInt8 (.-raw-in rdr)))

     (<= 0x60 code 0x6F)
     (bit-or (<< (- code codes/INT_PACKED_3_ZERO) 16) (rawIn/readRawInt16 (.-raw-in rdr)))

     (<= 0x70 code 0x73)
     (bit-or (<< (- code codes/INT_PACKED_4_ZERO) 24) (rawIn/readRawInt24 (.-raw-in rdr)))

     (<= 0x74 code 0x77)
     (let [packing (Long.fromNumber (- code codes/INT_PACKED_5_ZERO))
           i32 (Long.fromNumber (rawIn/readRawInt32 (.-raw-in rdr)))]
       (.toNumber (.or (.shiftLeft packing 32) i32)))

     (<= 0x78 code 0x7B)
     (let [packing (Long.fromNumber (- code codes/INT_PACKED_6_ZERO))
           i40 (Long.fromNumber (rawIn/readRawInt40 (.-raw-in rdr)))]
       (.toNumber (.or (.shiftLeft packing 40) i40)))

     (<= 0x7C code 0x7F)
     (let [packing (Long.fromNumber (- code codes/INT_PACKED_7_ZERO))
           i48 (Long.fromNumber (rawIn/readRawInt48 (.-raw-in rdr)))]
       (.toNumber (.or (.shiftLeft packing 48) i48)))

     (== code codes/INT)
     (rawIn/readRawInt64 (.-raw-in rdr))

     :default
     (let [o (read- rdr code)]
       (if (number? o) o
         (expected rdr "i64" code o))))))

(defn internalReadList [rdr length]
  (let [handler (getHandler- rdr "list")]
    (handler (readObjects- rdr length))))

(defn ^bytes internalReadBytes
  "called on codes/BYTES"
  ;; readFully returns a view on raw memory. here we copy values to get new buffer backing
  [rdr length]
  (js/Int8Array.from (rawIn/readFully (.-raw-in rdr) length)))

(defn ^bytes internalReadChunkedBytes
  "called on codes/BYTES_CHUNK"
  [rdr]
  (let [chunks (array-list)
        code (loop [code codes/BYTES_CHUNK]
               (if-not (== code codes/BYTES_CHUNK)
                 code
                 (let [cnt (readCount- rdr)] ;<== suppossed to be ranges/BYTE_CHUNK_SIZE
                   (.add chunks (internalReadBytes rdr cnt))
                   (recur (readNextCode rdr)))))]
    (if-not (== code codes/BYTES)
      (throw (js/Error. (str "conclusion of chunked bytes " code))))
    (.add chunks (internalReadBytes rdr (readCount- rdr)))
    (let [size (.size chunks)
          length (loop [length 0
                        i 0]
                   (if-not (< i size)
                     length
                     (recur (+ length (.-length (.get chunks i))) (inc i))))
          result (byte-array length)]
      (loop [pos 0
             i 0]
            (when (< i size)
              (let [chunk (.get chunks i)]
                (.set result chunk pos))
              (recur (+ pos (.-length (.get chunks i))) (inc i))))
      result)))

(defn ^string internalReadString [rdr length]
  (let [bytes  (rawIn/readFully (.-raw-in rdr) length)]
    (read-utf8-chars bytes 0 length)))

(defn ^string internalReadChunkedString [rdr length]
  (let [stringbuf (goog.string.StringBuffer. (internalReadString rdr length))]
    (loop []
      (let [code (readNextCode rdr)]
        (cond
          (or
           (== code (+ codes/STRING_PACKED_LENGTH_START 0))
           (== code (+ codes/STRING_PACKED_LENGTH_START 1))
           (== code (+ codes/STRING_PACKED_LENGTH_START 2))
           (== code (+ codes/STRING_PACKED_LENGTH_START 3))
           (== code (+ codes/STRING_PACKED_LENGTH_START 4))
           (== code (+ codes/STRING_PACKED_LENGTH_START 5))
           (== code (+ codes/STRING_PACKED_LENGTH_START 6))
           (== code (+ codes/STRING_PACKED_LENGTH_START 7)))
          (.append stringbuf (internalReadString rdr (- code codes/STRING_PACKED_LENGTH_START)))

          (== code codes/STRING)
          (.append stringbuf (internalReadString rdr (readCount- rdr)))

          (== code codes/STRING_CHUNK)
          (do
            (.append stringbuf (internalReadString rdr (readCount- rdr)))
            (recur))

          :else
          (expected rdr "chunked string" code))))
    (.toString stringbuf)))

(defn internalRead [rdr ^number code]
  (let []
    (cond
      (or (== code 0xFF)
          (<= 0x00 code 0x7F)
          (== code codes/INT))
      (internalReadInt rdr code)

      (== code codes/UTF8)
      (readUTF8 rdr)

      (== code codes/PUT_PRIORITY_CACHE)
      (readAndCacheObject rdr (getPriorityCache rdr))

      (== code codes/GET_PRIORITY_CACHE)
      (lookupCache rdr (getPriorityCache rdr) (readInt32 rdr))

      (or
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 0))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 1))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 2))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 3))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 4))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 5))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 6))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 7))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 8))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 9))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 10))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 11))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 12))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 13))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 14))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 15))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 16))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 17))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 18))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 19))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 20))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 21))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 22))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 23))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 24))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 25))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 26))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 27))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 28))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 29))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 30))
       (== code (+ codes/PRIORITY_CACHE_PACKED_START 31)))
      (lookupCache rdr (getPriorityCache rdr) (- code codes/PRIORITY_CACHE_PACKED_START))

      (or
       (== code (+ codes/STRUCT_CACHE_PACKED_START 0))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 1))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 2))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 3))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 4))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 5))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 6))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 7))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 8))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 9))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 10))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 11))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 12))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 13))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 14))
       (== code (+ codes/STRUCT_CACHE_PACKED_START 15)))
      (let [struct-type (lookupCache rdr (getStructCache rdr) (- code codes/STRUCT_CACHE_PACKED_START))]
        (handleStruct- rdr (.-tag struct-type) (.-fields struct-type)))

      (== code codes/MAP)
      (handleStruct- rdr "map" 1)

      (== code codes/SET)
      (handleStruct- rdr "set" 1)

      (== code codes/UUID)
      (handleStruct- rdr "uuid" 2)

      (== code codes/REGEX)
      (handleStruct- rdr "regex" 1)

      (== code codes/URI)
      (handleStruct- rdr "uri" 1)

      (== code codes/BIGINT)
      (handleStruct- rdr "bigint" 1)

      (== code codes/BIGDEC)
      (handleStruct- rdr "bigdec" 2)

      (== code codes/INST)
      (handleStruct- rdr "inst" 1)

      (== code codes/SYM)
      (handleStruct- rdr "sym" 2)

      (== code codes/KEY)
      (handleStruct- rdr "key" 2)

      (== code codes/INT_ARRAY)
      (handleStruct- rdr "int[]" 2)

      (== code codes/LONG_ARRAY)
      (handleStruct- rdr "long[]" 2)

      (== code codes/FLOAT_ARRAY)
      (handleStruct- rdr "float[]" 2)

      (== code codes/DOUBLE_ARRAY)
      (handleStruct- rdr "double[]" 2)

      (== code codes/BOOLEAN_ARRAY)
      (handleStruct- rdr "boolean[]" 2)

      (== code codes/OBJECT_ARRAY)
      (handleStruct- rdr "Object[]" 2)

      (or
       (== code (+ codes/BYTES_PACKED_LENGTH_START 0))
       (== code (+ codes/BYTES_PACKED_LENGTH_START 1))
       (== code (+ codes/BYTES_PACKED_LENGTH_START 2))
       (== code (+ codes/BYTES_PACKED_LENGTH_START 3))
       (== code (+ codes/BYTES_PACKED_LENGTH_START 4))
       (== code (+ codes/BYTES_PACKED_LENGTH_START 5))
       (== code (+ codes/BYTES_PACKED_LENGTH_START 6))
       (== code (+ codes/BYTES_PACKED_LENGTH_START 7)))
      (internalReadBytes rdr (- code codes/BYTES_PACKED_LENGTH_START))

      (== code codes/BYTES)
      (internalReadBytes rdr (readCount- rdr))

      (== code codes/BYTES_CHUNK)
      (internalReadChunkedBytes rdr)

      (or
       (== code (+ codes/STRING_PACKED_LENGTH_START 0))
       (== code (+ codes/STRING_PACKED_LENGTH_START 1))
       (== code (+ codes/STRING_PACKED_LENGTH_START 2))
       (== code (+ codes/STRING_PACKED_LENGTH_START 3))
       (== code (+ codes/STRING_PACKED_LENGTH_START 4))
       (== code (+ codes/STRING_PACKED_LENGTH_START 5))
       (== code (+ codes/STRING_PACKED_LENGTH_START 6))
       (== code (+ codes/STRING_PACKED_LENGTH_START 7)))
      (internalReadString rdr (- code codes/STRING_PACKED_LENGTH_START)) ;=> string

      (== code codes/STRING)
      (internalReadString rdr (readCount- rdr))

      (== code codes/STRING_CHUNK)
      (internalReadChunkedString rdr (readCount- rdr)) ;=> string

      (or
       (== code (+ codes/LIST_PACKED_LENGTH_START 0))
       (== code (+ codes/LIST_PACKED_LENGTH_START 1))
       (== code (+ codes/LIST_PACKED_LENGTH_START 2))
       (== code (+ codes/LIST_PACKED_LENGTH_START 3))
       (== code (+ codes/LIST_PACKED_LENGTH_START 4))
       (== code (+ codes/LIST_PACKED_LENGTH_START 5))
       (== code (+ codes/LIST_PACKED_LENGTH_START 6))
       (== code (+ codes/LIST_PACKED_LENGTH_START 7)))
      (internalReadList rdr (- code codes/LIST_PACKED_LENGTH_START))

      (== code codes/LIST)
      (internalReadList rdr (readCount- rdr))

      (== code codes/BEGIN_CLOSED_LIST)
      ; result = ((ConvertList) getHandler("list")).convertList(readClosedList());
      (let [handler (getHandler rdr "list")]
        (handler (readClosedList rdr)))

      (== code codes/BEGIN_OPEN_LIST)
      (let [handler (getHandler rdr "list")]
        ; result = ((ConvertList) getHandler("list")).convertList(readOpenList());
        (handler (readOpenList rdr)))

      (== code codes/TRUE)
      true

      (== code codes/FALSE)
      false

      (== code codes/NULL)
      nil

      (or
       (== code codes/DOUBLE)
       (== code codes/DOUBLE_0)
       (== code codes/DOUBLE_1))
      (let [handler (getHandler rdr "double")] ;=================>>>>>>>>>>>>>>>>>>>
        (handler (internalReadDouble rdr code)))

      (== code codes/FLOAT)
      (let [handler (getHandler rdr "float")] ;>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
        (handler (readRawFloat (.-raw-in rdr))))

      (== code codes/FOOTER)
      (let [calculatedLength (dec (rawIn/getBytesRead (.-raw-in rdr)))
            magic (+ (bit-shift-left code 24) (rawIn/readRawInt24 (.-raw-in rdr)))]
        (validateFooter rdr calculatedLength magic)
        (readObject rdr))

      (== code codes/STRUCTTYPE)
      (let [tag (readObject rdr)
            n-fields (readInt32 rdr)]
        (.add (getStructCache- rdr) (StructType. tag fields))
        (handleStruct- rdr tag fields))

      (== code codes/STRUCT)
      (let [struct-type (lookupCache rdr (getStructCache rdr) (readInt32 rdr))]
        (handleStruct- rdr (.-tag struct-type) (.-fields struct-type)))

      (== code codes/RESET_CACHES)
      (do
        (resetCaches rdr)
        (readObject rdr))

      :else
      (throw (js/Error. (str "unmatched code: " code))))))

(defrecord FressianReader [in raw-in lookup standardExtensionHandlers]
  Object
  ; (close [] (.close raw-in))
  IFressianReader
  (readNextCode [this] (rawIn/readRawByte raw-in))
  (readInt ^number [this] (internalReadInt this))
  (readInt32 ^number [this]
    (let [i (readInt this)]
      (if (or (< i I32_MIN_VALUE)  (< I32_MAX_VALUE i))
        (throw (js/Error. (str  "value " i " out of range for i32"))))
      i))
  (readCount- [this](readInt32 this))
  (read- [this code] (internalRead this code))
  (readObject [this] (read- this (readNextCode this)))
  (readFloat ^number [this]
    (let [code (readNextCode this)]
      (if (== code codes/FLOAT)
        (rawIn/readRawFloat raw-in)
        (let [o (read- this code)]
          (if (number? o)
            o
            (expected rdr "float" code o))))))
  (readDouble ^number [this]
    (internalReadDouble this (readNextCode this)))
  (readBoolean [this]
    (let [code (readNextCode this)]
      (if (== code codes/TRUE)
        true
        (if (== code codes/FALSE)
          false
          (let [o (read- rdr code)]
            (if (boolean? o)
              o
              (expected rdr "boolean" code o)))))))
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (getStructCache- [this]
    (or structCache
        (let [c (array-list)]
          (set! (.-structCache this) c)
          c)))
  (getPriorityCache- [this]
    (or getPriorityCache
        (let [c (array-list)]
          (set! (.-getPriorityCache this) c)
          c)))
  (resetCaches- [this]
    (some-> priorityCache (.clear))
    (some-> structCache (.clear)))
  (getHandler- [this ^string tag]
    (let [handler (lookup this tag)]
      (if (nil? handler)
        (throw (js/Error. (str "no read handler for tag: " (pr-str tag))))
        handler)))
  (handleStruct- [this ^string tag ^number fields]
    (let [handler (or (lookup this tag) (.get standardExtensionHandlers tag))]
      (if (nil? handler)
        (TaggedObject. tag (readObjects- this fields))
        (handler this tag fields))))
  (readObjects- ^Array [this ^number length] ;=> 'object[]'
    (let [objects (make-array length)]
      (loop [i 0]
        (if-not (< i length)
          objects
          (do
            (aset objects i (readObject this))
            (recur (inc i)))))))
  (readClosedList- [this]
    (let [objects (array-list)]
      (loop []
        (let [code (readNextCode this)]
          (if (== code codes/END_COLLECTION)
            (.toArray objects)
            (do
              (.add objects (read rdr code))
              (recur)))))))
  (readOpenList- [this]
    (let [objects (array-list)]
      (loop []
        (let [code (try
                     (readNextCode this)
                     (catch js/Error _ ;=<<<<<<<<< EOF
                       codes/END_COLLECTION))]
          (if (== code codes/END_COLLECTION)
            (.toArray objects)
            (do
              (.add objects (read rdr code))
              (recur)))))))
  (readAndCacheObject- [this ^ArrayList cache]
    (let [index (.size cache)
          ; _(.add cache codes/UNDER_CONSTRUCTION)
          o (readObject rdr)]
      (.add cache index o)
      o))
  (lookupCache- [this cache index]
    (if (< index (.size cache))
      (.get cache index)
      (throw (js/Error. (str "Requested object beyond end of cache at " index)))))
  (validateFooter- [this]
    (let [calculatedLength (rawIn/getBytesRead raw-in)
          magicFromStream (rawIn/readRawInt32 raw-in)]
      (validateFooter- this calculatedLength magicFromStream)))
  (validateFooter- [this calculatedLength ^number magicFromStream]
    (if-not (== magicFromStream codes/FOOTER_MAGIC)
      (throw (js/Error. (str "Invalid footer magic, expected " codes/FOOTER_MAGIC " got " code)))
      (let [lengthFromStream (rawIn/readRawInt32 raw-in)]
        (if-not (== lengthFromStream calculatedLength)
          (throw (js/Error. (str "Invalid footer lenght, expected " calculatedLength " got " lengthFromStream)))
          (do
            (rawIn/validateChecksum raw-in)
            (rawIn/reset raw-in)
            (resetCaches this)))))))



(defn readSet [rdr])
(defn readMap [rdr])
(defn readIntArray [rdr])
(defn readLongArray [rdr])

(def default-read-handlers
  {"list" (fn [objectArray] (vec objectArray)) ;;is this signature right?
   "utf8" #(readUTF8 %1) ;<= for tagged use, but default is still code
   })

(defn build-lookup
  [userHandlers]
  (let [handlers (merge default-read-handlers userHandlers)]
    (fn lookup [rdr tag]
      (get handlers tag))))

(defn standardExtensionHandlers []
  (reify Object
    (get [this tag] nil)))

(defn reader
  ([in] (reader in nil))
  ([in user-handlers]
   (let [handlers (merge default-read-handlers user-handlers)
         lookup (build-lookup handlers)
         raw-in (rawIn/raw-input in)
         seh (standardExtensionHandlers)]
     (FressianReader. in raw-in lookup seh))))
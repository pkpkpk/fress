(ns fress.reader
  (:require-macros [fress.macros :refer [<< >>>]])
  (:require [fress.impl.raw-input :as rawIn]
            [fress.impl.codes :as codes]
            [fress.impl.ranges :as ranges]
            [fress.util :as util :refer [expected byte-array log]])
  (:import [goog.math Long]))

(def ^:dynamic *EOF-after-footer?* true) ;goog define?
(def ^:dynamic *keywordize-keys* false) ;; this can be lossy!

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
                                           (<< (bit-and ch 0x1f) 6)
                                           (bit-and ch1 0x3f)))
                               (recur (+ pos 2)))
            (= ch>>4 14) (let [ch1 (aget source (inc pos))
                               ch2 (aget source (+ pos 2))]
                           (.push buf (bit-or
                                       (<< (bit-and ch 0x0f) 12)
                                       (<< (bit-and ch1 0x03f) 6)
                                       (bit-and ch2 0x3f)))
                           (recur (+ pos 3)))
            :default (throw (str "Invalid UTF-8: " ch))))))
    (.apply (.-fromCharCode js/String) nil buf)))

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
  (readClosedList [this])
  (readOpenList [this])
  (readAndCacheObject- [this cache])
  (lookupCache [this cache index])
  (validateFooter [this] [this calculatedLength magicFromStream])
  (handleStruct- [this ^string tag fields])
  (getHandler- [this ^string tag])
  (getPriorityCache- [this])
  (getStructCache- [this])
  (resetCaches [this]))


(defn readUTF8
  "this uses TextDecoder on raw utf8 bytes instead of using js on compressed
   fressian string bytes"
  [rdr]
  (let [length (readCount- rdr)
        bytes (rawIn/readFully (:raw-in rdr) length)]
    (.decode util/TextDecoder bytes)))

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
           i40 (rawIn/readRawInt40L (.-raw-in rdr))]
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

(def magicL (.shiftLeft (Long.fromNumber codes/FOOTER) 24))


(defn internalRead [rdr ^number code]
  (let []
    (cond

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; primitives

      ;;need char!

      (== code codes/UTF8) ;; first just because its a core use case
      (readUTF8 rdr)

      (== code codes/ERROR)
      (readObject rdr)

      (== code codes/STR)
      (let [length (readInt rdr)]
        (if (zero? length)
          ""
          (let [ptr (readInt rdr) ;can be uncompressed u32
                memory (.-memory (:in (:raw-in rdr)))
                buf (.-buffer memory)]
            (.decode util/TextDecoder (js/Int8Array. buf ptr length)))))

      (== code codes/TRUE)
      true

      (== code codes/FALSE)
      false

      (== code codes/NULL)
      nil

      (or (== code 0xFF)
          (<= 0x00 code 0x7F)
          (== code codes/INT))
      (internalReadInt rdr code)

      (or (== code codes/DOUBLE)
          (== code codes/DOUBLE_0)
          (== code codes/DOUBLE_1))
      (internalReadDouble rdr code)

      (== code codes/FLOAT)
      (rawIn/readRawFloat (:raw-in rdr))

      (<= codes/BYTES_PACKED_LENGTH_START code 215)
      (internalReadBytes rdr (- code codes/BYTES_PACKED_LENGTH_START))

      (== code codes/BYTES)
      (internalReadBytes rdr (readCount- rdr))

      (== code codes/BYTES_CHUNK)
      (internalReadChunkedBytes rdr)

      (<= codes/STRING_PACKED_LENGTH_START code 225)
      (internalReadString rdr (- code codes/STRING_PACKED_LENGTH_START))

      (== code codes/STRING)
      (internalReadString rdr (readCount- rdr))

      (== code codes/STRING_CHUNK)
      (internalReadChunkedString rdr (readCount- rdr))

      (<= codes/LIST_PACKED_LENGTH_START code 235)
      (internalReadList rdr (- code codes/LIST_PACKED_LENGTH_START))

      (== code codes/LIST)
      (internalReadList rdr (readCount- rdr))

      (== code codes/BEGIN_CLOSED_LIST)
      (let [handler (getHandler- rdr "list")]
        (handler (readClosedList rdr)))

      (== code codes/BEGIN_OPEN_LIST)
      (let [handler (getHandler- rdr "list")]
        (handler (readOpenList rdr)))

      (== code codes/FOOTER)
      (let [calculatedLength (dec (rawIn/getBytesRead (.-raw-in rdr)))
            i24L (Long.fromNumber (rawIn/readRawInt24 (.-raw-in rdr)))]
        (validateFooter rdr calculatedLength (.toNumber (.add magicL i24L)))
        (readObject rdr))

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; extended types

      (== code codes/MAP)
      (handleStruct- rdr "map" 1)

      (== code codes/SET)
      (handleStruct- rdr "set" 1)

      (== code codes/_UUID)
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

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
      ;; typed arrays

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

      ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

      (== code codes/PUT_PRIORITY_CACHE)
      (readAndCacheObject- rdr (getPriorityCache- rdr))

      (== code codes/GET_PRIORITY_CACHE)
      (lookupCache rdr (getPriorityCache- rdr) (readInt32 rdr))

      ;; this and struct cache need testing
      (<= codes/PRIORITY_CACHE_PACKED_START code 159)
      (lookupCache rdr (getPriorityCache- rdr) (- code codes/PRIORITY_CACHE_PACKED_START))

      (<= codes/STRUCT_CACHE_PACKED_START code 175)
      (let [struct-type (lookupCache rdr (getStructCache- rdr) (- code codes/STRUCT_CACHE_PACKED_START))]
        (handleStruct- rdr (.-tag struct-type) (.-fields struct-type)))

      (== code codes/STRUCTTYPE)
      (let [tag (readObject rdr)
            n-fields (readInt32 rdr)]
        (.add (getStructCache- rdr) (StructType. tag n-fields))
        (handleStruct- rdr tag n-fields))

      (== code codes/STRUCT)
      (let [struct-type (lookupCache rdr (getStructCache- rdr) (readInt32 rdr))]
        (handleStruct- rdr (.-tag struct-type) (.-fields struct-type)))

      (== code codes/RESET_CACHES)
      (do
        (resetCaches rdr)
        (readObject rdr))

      :else
      (throw (js/Error. (str "unmatched code: " code))))))

(defrecord FressianReader [in raw-in lookup priorityCache structCache]
  IFressianReader
  (readNextCode [this] (rawIn/readRawByte raw-in))
  (readInt ^number [this] (internalReadInt this))
  (readInt32 ^number [this]
    (let [i (readInt this)]
      (if (or (< i util/I32_MIN_VALUE)  (< util/I32_MAX_VALUE i))
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
            (expected this "float" code o))))))
  (readDouble ^number [this]
    (internalReadDouble this (readNextCode this)))
  (readBoolean ^boolean [this]
    (let [code (readNextCode this)]
      (if (== code codes/TRUE)
        true
        (if (== code codes/FALSE)
          false
          (let [o (read- this code)]
            (if (boolean? o)
              o
              (expected this "boolean" code o)))))))
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (getStructCache- [this]
    (or structCache
        (let [c (array-list)]
          (set! (.-structCache this) c)
          c)))
  (getPriorityCache- [this]
    (or priorityCache
        (let [c (array-list)]
          (set! (.-priorityCache this) c)
          c)))
  (resetCaches [this]
    (some-> priorityCache (.clear))
    (some-> structCache (.clear)))
  (getHandler- [this ^string tag]
    (let [handler (lookup this tag)]
      (if (nil? handler)
        (throw (js/Error. (str "no read handler for tag: " (pr-str tag))))
        handler)))
  (handleStruct- [this ^string tag ^number fields]
    (let [handler (lookup this tag)]
      (if (nil? handler)
        (TaggedObject. tag (readObjects- this fields))
        (handler this tag fields))))
  (readObjects- ^array [this ^number length] ;=> 'object[]'
    (let [objects (make-array length)]
      (loop [i 0]
        (if-not (< i length)
          objects
          (do
            (aset objects i (readObject this))
            (recur (inc i)))))))
  (readClosedList [this]
    (let [objects (array-list)]
      (loop []
        (let [code (readNextCode this)]
          (if (== code codes/END_COLLECTION)
            (.toArray objects)
            (do
              (.add objects (read- this code))
              (recur)))))))
  (readOpenList [this]
    (let [objects (array-list)]
      (loop []
        (let [code (try
                     (readNextCode this)
                     (catch js/Error _ ;=<<<<<<<<< EOF
                       codes/END_COLLECTION))]
          (if (== code codes/END_COLLECTION)
            (.toArray objects)
            (do
              (.add objects (read- this code))
              (recur)))))))
  (readAndCacheObject- [this ^ArrayList cache]
    (let [index (.size cache)
          o (readObject this)]
      (.add cache o)
      o))
  (lookupCache [this cache index]
    (if (< index (.size cache))
      (.get cache index)
      (throw (js/Error. (str "Requested object beyond end of cache at " index)))))
  (validateFooter [this]
    (let [calculatedLength (rawIn/getBytesRead raw-in)
          magicFromStream (rawIn/readRawInt32 raw-in)]
      (validateFooter this calculatedLength magicFromStream)))
  (validateFooter [this calculatedLength ^number magicFromStream]
    (if-not (== magicFromStream codes/FOOTER_MAGIC)
      (throw (js/Error. (str "Invalid footer magic, expected " codes/FOOTER_MAGIC " got " magicFromStream)))
      (let [lengthFromStream (rawIn/readRawInt32 raw-in)]
        (if-not (== lengthFromStream calculatedLength)
          (throw (js/Error. (str "Invalid footer length, expected " calculatedLength " got " lengthFromStream)))
          (do
            (rawIn/validateChecksum raw-in)
            (when ^boolean *EOF-after-footer?* ; provoke EOF even when room
              (rawIn/close (.-raw-in this)))
            (resetCaches this)))))))

(defn readSet [rdr _ _]
  (let [lst (readObject rdr)]
    (into #{} lst)))

(defn readMap [rdr _ _]
  (if-not ^boolean *keywordize-keys*
    (apply hash-map (readObject rdr))
    (if-let [in (not-empty (readObject rdr))]
      (loop [in in out (transient (.-EMPTY PersistentHashMap))]
        (if-not in
          (persistent! out)
          (let [k (first in)
                key (if (string? k) (keyword k) k)]
            (recur (nnext in) (assoc! out key (second in))))))
      {})))

(defn readIntArray [rdr _ _]
  (let [length (readInt rdr)
        arr (js/Int32Array. length)]
    (loop [i 0]
      (when (< i length)
        (aset arr i (readInt rdr))
        (recur (inc i))))
    arr))

(defn readShortArray [rdr _ _]
  (let [length (readInt rdr)
        arr (js/Int16Array. length)]
    (loop [i 0]
      (when (< i length)
        (aset arr i (readInt rdr))
        (recur (inc i))))
    arr))

(defn ^array readLongArray [rdr _ _] ;=> regular Array<Number>
  (let [length (readInt rdr)
        arr (make-array length)]
    (loop [i 0]
      (when (< i length)
        (aset arr i (readInt rdr))
        (recur (inc i))))
    arr))

(defn readFloatArray [rdr _ _]
  (let [length (readInt rdr)
        arr (js/Float32Array. length)]
    (loop [i 0]
      (when (< i length)
        (aset arr i (readFloat rdr))
        (recur (inc i))))
    arr))

(defn readDoubleArray [rdr _ _]
  (let [length (readInt rdr)
        arr (js/Float64Array. length)]
    (loop [i 0]
      (when (< i length)
        (aset arr i (readDouble rdr))
        (recur (inc i))))
    arr))

(defn ^array readObjectArray [rdr _ _] ;=> regular Array<Object>
  (let [length (readInt rdr)
        arr (make-array length)]
    (loop [i 0]
      (when (< i length)
        (aset arr i (readObject rdr))
        (recur (inc i))))
    arr))

(defn ^array readBooleanArray [rdr _ _] ;=> regular Array<Boolean>
  (let [length (readInt rdr)
        arr (make-array length)]
    (loop [i 0]
      (when (< i length)
        (aset arr i (readBoolean rdr))
        (recur (inc i))))
    arr))

(defn readUUID [rdr _ _]
  ; adapted from https://github.com/kawasima/fressian-cljs/blob/master/src/cljs/fressian_cljs/uuid.cljs
  (let [bytes (readObject rdr)
        _(assert (== (alength bytes) 16) (str "invalid UUID buffer size:" (alength bytes)))
        offset (atom 0)
        acc #js[]]
    (doseq [n [4 2 2 2 6]]
      (let [token (map (fn [i8]
                         (-> (+ (util/i8->u8 i8) 0x100)
                             (.toString 16)
                             (.substr 1)))
                       (take n (drop @offset (array-seq bytes))))]
        (swap! offset + n)
        (.push acc (apply str token))))
    (UUID. (.join acc "-") nil)))

(defn readRegex [rdr _ _]
  (let [source (readObject rdr)]
    (re-pattern source)))

(defn readUri [rdr _ _]
  (let [uri (readObject rdr)]
    (goog.Uri. uri)))

(defn readInst [rdr _ _]
  (let [time (readInt rdr)
        date (js/Date.)]
    (.setTime date time)
    date))

(defn readKeyword [rdr _ _]
  (keyword (readObject rdr) (readObject rdr)))

(defn readSymbol [rdr _ _]
  (symbol (readObject rdr) (readObject rdr)))

(defn readRecord [rdr tag component-count name->map-ctor]
  (let [rname (readObject rdr)
        rmap (readObject rdr)]
    (if-let [rcons (get name->map-ctor (name rname))]
      (rcons rmap)
      (TaggedObject. "record" #js[rname rmap]))))

(def default-read-handlers
  {"list" (fn [objectArray] (vec objectArray)) ;;diff sig, called by internalReadList
   "utf8" #(readUTF8 %1) ;<= for tagged use, but default is still code
   "set" readSet
   "map" readMap
   "int[]" readIntArray
   "short[]" readShortArray
   "long[]" readLongArray
   "float[]" readFloatArray
   "double[]" readDoubleArray
   "boolean[]" readBooleanArray
   "Object[]" readObjectArray
   "uuid" readUUID
   "regex" readRegex
   "uri" readUri
   "inst" readInst
   "key" readKeyword
   "sym" readSymbol})

(defn add-handler [acc [tag handler]]
  (if (coll? tag)
    (reduce (fn [acc k] (assoc acc k handler)) acc tag)
    (assoc acc tag handler)))

(defn ^boolean valid-handler-key?
  [k]
  (if (coll? k)
    (every? string? k)
    (string? k)))

(defn build-lookup
  [user-handlers name->map-ctor]
  (let [handlers (reduce add-handler default-read-handlers user-handlers)]
    (fn lookup [rdr tag]
      (if (= "record" tag)
        (get user-handlers "record"
             (fn [rdr tag field-count]
               (readRecord rdr tag field-count name->map-ctor)))
        (get handlers tag)))))

(defn valid-user-handlers? [uh]
  (and (map? uh)
       (every? fn? (vals uh))
       (every? valid-handler-key? (keys uh))))

(defn valid-name->map-ctor? [m]
  (and (map? m)
       (every? string? (keys m))
       (every? fn? (vals m))))

(defn reader
  [in & {:keys [handlers checksum? offset name->map-ctor]
         :or {handlers nil, checksum? false} :as opts}]
  (when handlers
    (assert (valid-user-handlers? handlers)))
  (when name->map-ctor
    (assert (valid-name->map-ctor? name->map-ctor)))
  (when offset ;; doesn't check in memory range or size
    (assert (util/valid-pointer? offset)))
  (let [offset (or offset 0)
        lookup (build-lookup (merge default-read-handlers handlers) name->map-ctor)
        raw-in (rawIn/raw-input in offset checksum?)]
    (FressianReader. in raw-in lookup nil nil)))
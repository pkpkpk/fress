(ns fress.reader
  (:require-macros [fress.macros :refer [<< >>>]])
  (:require [fress.impl.raw-input :as rawIn]
            [fress.codes :as codes]
            [fress.ranges :as ranges]
            [goog.string :as gstring]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

(def ^:const MAX_SAFE_INT (.-MAX_SAFE_INTEGER js/Number))
(def ^:const MIN_SAFE_INT (.-MIN_SAFE_INTEGER js/Number))

(defn ^int internalReadInt32 [this])
(defn ^bytes internalReadString [this])
(defn ^bytes internalReadStringBuffer [this])
(defn ^string internalReadChunkedString [this])
(defn ^bytes internalReadBytes [this])
(defn ^bytes internalReadChunkedBytes [this])
(defn ^number internalReadDouble [this])

(defprotocol IFressianReader
  (read [this code])
  (readNextCode [this])
  (readBoolean [this])
  (readInt [this])
  (readDouble [this])
  (readFloat [this])
  (readInt32 [this])
  (readObject [this])
  (readCount [this])
  (readObjects [this])
  (readClosedList [this])
  (readOpenList [this])
  (readAndCacheObject [this])
  (validateFooter [this]
                  [this calculatedLength magicFromStream])
  (handleStruct [this ^string tag fields])
  (getHandler [this ^string tag])
  (getPriorityCache [this])
  (getStructCache [this])
  (resetCaches [this]))

(defn ^number internalReadInt [rdr]
  (let [code (readNextCode rdr)]
    (cond
      (= code 0xFF) -1

      (<= 0x00 code 0x3F)
      (bit-and code 0xFF)

      (<= 0x40 code 0x5F)
      (bit-or (<< (- code codes/INT_PACKED_2_ZERO) 8) (rawIn/readRawInt8 (.-raw-in rdr)))

      (<= 0x60 code 0x6F)
      (bit-or (<< (- code codes/INT_PACKED_3_ZERO) 16) (rawIn/readRawInt16 (.-raw-in rdr)))

      (<= 0x70 code 0x73)
      (bit-or (<< (- code codes/INT_PACKED_4_ZERO) 24) (rawIn/readRawInt24 (.-raw-in rdr)))

      (<= 0x74 code 0x77)
      (let [packing (goog.math.Long. (- code codes/INT_PACKED_5_ZERO))
            i32 (goog.math.Long. (rawIn/readRawInt32 (.-raw-in rdr)))]
        (.toNumber (.or (.shiftLeft packing 32) i32)))

      (<= 0x78 code 0x7B)
      (bit-or (<< (- code codes/INT_PACKED_6_ZERO ) 40) (rawIn/readRawInt40 (.-raw-in rdr)))

      (<= 0x7C code 0x7F)
      (bit-or (<< (- code codes/INT_PACKED_7_ZERO) 48) (rawIn/readRawInt48 (.-raw-in rdr)))

      (= code codes/INT)
      (rawIn/readRawInt64 (.-raw-in rdr))

      :default
      (let [o (read rdr code)]
        (if (number? o) o
          (throw (js/Error. (str "expected int64" code o))))))))

(defrecord FressianReader [in raw-in lookup]
  IFressianReader
  (readNextCode [this] (rawIn/readRawByte raw-in))
  (readInt [this] (internalReadInt this)))


(def default-read-handlers
  {})

(defn reader
  ([in] (reader in nil))
  ([in user-handlers]
   (let [handlers (merge default-read-handlers user-handlers)
         raw-in (rawIn/raw-input in)]
     (FressianReader. in raw-in handlers))))
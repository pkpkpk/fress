(ns fress.reader
  (:require [fress.impl.raw-input :as rawIn]
            [fress.codes :as codes]
            [fress.ranges :as ranges]
            [goog.string :as gstring]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

(defn ^long internalReadInt [this])
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

(defrecord Reader [in]
  IFressianReader
  (readNextCode [this] (rawIn/readRawByte in))
  (readInt [this] (internalReadInt this)))
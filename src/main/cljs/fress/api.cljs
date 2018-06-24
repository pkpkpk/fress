(ns fress.api
  (:require [fress.reader :as r]
            [fress.impl.raw-input :as rawIn]
            [fress.impl.raw-output :as rawOut]
            [fress.writer :as w]
            [fress.impl.buffer :as buf]
            [fress.util :as util]))

(defn create-reader
  "Create a fressian reader targeting in, which must be compatible
   with clojure.java.io/input-stream.  Handlers must be a map of
   tag => ReadHandler wrapped in associative-lookup. See
   clojure-read-handlers for an example."
  [in & {:keys [handlers validateAdler? offset]
         :or {handlers nil, offset 0, validateAdler? false} :as opts}]
  (when handlers (assert (r/valid-user-handlers? handlers)))
  (let [lookup (r/build-lookup (merge r/default-read-handlers handlers))
        raw-in (rawIn/raw-input in offset validateAdler?)]
    (r/FressianReader. in raw-in lookup nil nil)))

(defn read-object
  "Read a single object from a fressian reader."
  [rdr]
  (r/readObject rdr))

(defn tagged-object?
  "Returns true if o is a tagged object, which will occur when
   the reader does not recognized a specific type.  Use tag
   and tagged-value to access the contents of a tagged-object."
  [o]
  (instance? r/TaggedObject o))

(defn tag
  "Returns the tag if object is a tagged-object, else nil."
  [obj]
  (when (tagged-object? obj)
    (get obj :tag)))

(defn tagged-value
  "Returns the value (an Object arrray) wrapped by obj, or nil
   if obj is not a tagged object."
  [obj]
  (when (tagged-object? obj)
    (get obj :val)))



(defn create-writer
  "Create a fressian writer targeting out.
   that combines userHandlers with the normal type handlers
   built into Fressian."
  [out & {:keys [handlers] :as opts}]
  (when handlers (assert (w/valid-user-handlers? handlers)))
  (let [lookup-fn (w/build-handler-lookup handlers)
        raw-out (rawOut/raw-output out)]
    (w/FressianWriter. out raw-out nil nil lookup-fn)))

(defn write-object
  "Write a single object to a fressian writer." ;<= ret?
  [wrt o]
  (w/writeObject wrt o))

(defn write-utf8
  "write a string as raw utf-8 bytes"
  ([wrt s](write-utf8 wrt s false))
  ([wrt s tag?]
   (assert (string? s))
   (binding [w/*write-raw-utf8* true
             w/*write-utf8-tag* tag?]
     (w/writeString wrt s))))



(defn buffer
  ([])
  ([size]))

(defn flush-to [])
(ns fress.util
  (:require-macros [fress.macros :as mac])
  (:require  [fress.impl.bigint :as bn]
             [goog.crypt :as gcrypt]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

(def ^:dynamic *debug* false)

(defn dbg [& args]
  (when ^boolean goog.DEBUG
    (when *debug*
      (apply log args))))

(defn valid-pointer? [ptr]
  (and (number? ptr)
       (js/isFinite ptr)
       (<= 0 ptr)
       (integer? ptr)))

(def TextEncoder
  (if (exists? js/TextEncoder)
    (js/TextEncoder. "utf8")
    (if ^boolean (mac/nodejs?)
      (let [te (.-TextEncoder (js/require "util"))]
        (new te))
      (reify Object
        (encode [_ s]
          (js/Int8Array. (gcrypt/stringToUtf8ByteArray s)))))))

(def TextDecoder
  (if (exists? js/TextDecoder)
    (js/TextDecoder. "utf8")
    (if ^boolean (mac/nodejs?)
      (let [td (.-TextDecoder (js/require "util"))]
        (new td "utf8"))
      (reify Object
        (decode [this bytes]
          (gcrypt/utf8ByteArrayToString bytes))))))

(extend-type js/Int8Array
  IEquiv
  (-equiv [this that] (= (array-seq this) (array-seq that))))

(extend-type js/Uint8Array
  IEquiv
  (-equiv [this that] (= (array-seq this) (array-seq that))))

(extend-type ArrayList
  Object
  (get [this i] (aget (.-arr this) i))
  (set [this i o] (aset (.-arr this) i o)))

(def ^:const u8_MAX_VALUE 255) ;0xff

(def ^:const i16_MIN_VALUE -32767)
(def ^:const i16_MAX_VALUE 32767)
(def ^:const u16_MAX_VALUE 65535)

(def ^:const i32_MIN_VALUE -2147483648)
(def ^:const i32_MAX_VALUE 2147483647)
(def ^:const u32_MAX_VALUE 0xffffffff)

(def ^:const f32_MIN_VALUE 1.4E-45)
(def ^:const f32_MIN_NORMAL 1.17549435E-38)
(def ^:const f32_MAX_VALUE 3.4028235E38)

(def i64_MIN_VALUE (bn/hex->signed-bigint "8000000000000000"))
(def i64_MAX_VALUE (bn/hex->signed-bigint "7fffffffffffffff"))

(def i128_MIN_VALUE (bn/hex->signed-bigint "80000000000000000000000000000000"))
(def i128_MAX_VALUE (bn/hex->signed-bigint "7fffffffffffffffffffffffffffffff"))

(def u64_MAX_VALUE (js/BigInt "0xffffffffffffffff"))
(def u128_MAX_VALUE (js/BigInt "0xffffffffffffffffffffffffffffffff"))

(def ^:const MAX_SAFE_INTEGER 0x1fffffffffffff)

(defonce isBigEndian
  (-> (.-buffer (js/Uint32Array. #js[0x12345678]))
    (js/Uint8Array. )
    (aget 0)
    (== 0x12)))

(defn expected
  ([rdr tag code]
   (let [index (.-raw-in rdr)
         msg (str "Expected " tag " with code: " code "prior to index: " index )]
     (throw (js/Error. msg))))
  ([rdr tag code o]
   (let [index (.-raw-in rdr)
         msg (str "Expected " tag " with code: " code "prior to index: " index
                  ", got " (type o) " " (pr-str o) "instead")]
     (throw (js/Error. msg)))))


(defmulti
  ^{:doc "signed byte array. allocates new buffer"}
  byte-array type)

(defmethod byte-array js/Number [n] (js/Int8Array. n))
(defmethod byte-array PersistentVector [v] (js/Int8Array. (into-array v)))
(defmethod byte-array js/Array [a] (js/Int8Array. a))
(defmethod byte-array ArrayList [al] (js/Int8Array. (.toArray al)))
(defmethod byte-array js/String [s] (.encode TextEncoder s))
(defmethod byte-array js/Uint8Array [a] (js/Int8Array. a))
(defmethod byte-array js/Int8Array [a] (js/Int8Array. a))

(def i8-array byte-array)

(defmulti
  ^{:doc "unsigned byte array. allocates new buffer"}
  u8-array type)
(defmethod u8-array js/Number [n] (js/Uint8Array. n))
(defmethod u8-array PersistentVector [v] (js/Uint8Array. (into-array v)))
(defmethod u8-array js/Array [a] (js/Uint8Array. a))
(defmethod u8-array ArrayList [al] (js/Uint8Array. (.toArray al)))
(defmethod u8-array js/String [s] (.encode TextEncoder s))
(defmethod u8-array js/Int8Array [a] (js/Uint8Array. a))
(defmethod u8-array js/Uint8Array [a] (js/Uint8Array. a))

(defmulti  i32-array type)
(defmethod i32-array js/Number [n] (js/Int32Array. n))
(defmethod i32-array PersistentVector [v] (js/Int32Array. (into-array v)))
(defmethod i32-array js/Array [a] (js/Int32Array. a))
(defmethod i32-array ArrayList [al] (js/Int32Array. (.toArray al)))

(defmulti  f32-array type)
(defmethod f32-array js/Number [n] (js/Float32Array. n))
(defmethod f32-array PersistentVector [v] (js/Float32Array. (into-array v)))
(defmethod f32-array js/Array [a] (js/Float32Array. a))
(defmethod f32-array ArrayList [al] (js/Float32Array. (.toArray al)))

(defmulti  f64-array type)
(defmethod f64-array js/Number [n] (js/Float64Array. n))
(defmethod f64-array PersistentVector [v] (js/Float64Array. (into-array v)))
(defmethod f64-array js/Array [a] (js/Float64Array. a))
(defmethod f64-array ArrayList [al] (js/Float64Array. (.toArray al)))

(defn time->inst [time] (doto (js/Date.) (.setTime time)))

(def i8->u8
  (let [bytea (js/Uint8Array. 1)]
    (fn ^number [i8]
      (aset bytea 0 i8)
      (aget bytea 0))))

(def u8->i8
  (let [bytea (js/Int8Array. 1)]
    (fn ^number [u8]
      (aset bytea 0 u8)
      (aget bytea 0))))

(def bigint? bn/bigint?)

(def bigint bn/bigint)

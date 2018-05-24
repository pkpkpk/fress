(ns fress.reader-test
  (:require-macros [fress.macros :refer [>>>]]
                   [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :as casync
             :refer [close! put! take! alts! <! >! chan promise-chan timeout]]
            [cljs.test :refer-macros [deftest is testing async]]
            [fress.impl.raw-output :as rawOut]
            [fress.impl.raw-input :as rawIn]
            [fress.codes :as codes]
            [fress.ranges :as ranges]
            [fress.reader :as r]
            [fress.test-helpers :as helpers
             :refer [log jvm-byteseq is= byteseq overflow into-bytes]]))

(defn rawbyteseq [rdr]
  (let [raw (.-raw-in rdr)
        acc #js[]]
    (loop []
      (let [byte (rawIn/readRawByte raw)]
        (if-not byte
          (vec acc)
          (do
            (.push acc byte)
            (recur)))))))



(def int-samples
  [
   {:form "(short 55)", :value 55, :bytes [55], :rawbytes [55]}
   {:form "(short -55)", :value -55, :bytes [79 -55], :rawbytes [79 201]}
   {:form "(short -32700)", :value -32700, :bytes [103 -128 68], :rawbytes [103 128 68]}
   {:form "(short 32700)", :value 32700, :bytes [104 127 -68], :rawbytes [104 127 188]}
   {:form "(short -32768)", :value -32768, :bytes [103 -128 0], :rawbytes [103 128 0]} ; min i16
   {:form "(short 32767)", :value 32767, :bytes [104 127 -1], :rawbytes [104 127 255]} ; max i16
   {:form "(int      -65534)", :value -65534, :bytes [103 0 2], :rawbytes [103 0 2]}
   {:form "(int       65534)", :value 65534, :bytes [104 -1 -2], :rawbytes [104 255 254]}
   {:form "(int     -262136)", :value -262136, :bytes [100 0 8], :rawbytes [100 0 8]}
   {:form "(int      262136)", :value 262136, :bytes [107 -1 -8], :rawbytes [107 255 248]}
   {:form "(int     1048544)", :value 1048544, :bytes [114 15 -1 -32], :rawbytes [114 15 255 224]}
   {:form "(int    -1048544)", :value -1048544, :bytes [113 -16 0 32], :rawbytes [113 240 0 32]}
   {:form "(int   -16776704)", :value -16776704, :bytes [113 0 2 0], :rawbytes [113 0 2 0]}
   {:form "(int    16776704)", :value 16776704, :bytes [114 -1 -2 0], :rawbytes [114 255 254 0]}
   {:form "(int  -134213632)", :value -134213632, :bytes [117 -8 0 16 0], :rawbytes [117 248 0 16 0]}
   {:form "(int   134213632)", :value 134213632, :bytes [118 7 -1 -16 0], :rawbytes [118 7 255 240 0]}
   {:form "(int  1073709056)", :value 1073709056, :bytes [118 63 -1 -128 0], :rawbytes [118 63 255 128 0]}
   {:form "(int -1073709056)", :value -1073709056, :bytes [117 -64 0 -128 0], :rawbytes [117 192 0 128 0]}
   {:form "(int -2147483648)", :value -2147483648, :bytes [117 -128 0 0 0], :rawbytes [117 128 0 0 0]} ;min i32
   {:form "(int  2147483647)", :value 2147483647, :bytes [118 127 -1 -1 -1], :rawbytes [118 127 255 255 255]} ;max i32

   ;MAX_SAFE_INT
   ; {:form "(long 9007199254740991)", :value 9007199254740991, :bytes [-8 0 31 -1 -1 -1 -1 -1 -1], :rawbytes [248 0 31 255 255 255 255 255 255]}


   ])

(deftest readInt-test
  (doseq [{:keys [form bytes value rawbytes]} int-samples]
    (testing form
      (let [rdr (r/reader (into-bytes bytes))]
        (when rawbytes
          (is= rawbytes (rawbyteseq rdr))
          (rawIn/reset (:raw-in rdr)))
        (is= value (r/readInt rdr)))))
  )

; (= -4294967296 (bit-shift-left (- 117 118) 32))

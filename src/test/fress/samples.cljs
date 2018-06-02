(ns fress.samples
  (:require-macros [fress.test-macros :as mac])
  (:require [cljs-node-io.core :as io :refer [slurp spit]]
            [cljs-node-io.fs :as fs]
            [cljs.tools.reader :refer [read-string]]))

(def int-samples
  [{:form "Short/MIN_VALUE", :value -32768, :bytes [103 -128 0], :rawbytes [103 128 0]}
   {:form "Short/MAX_VALUE", :value 32767, :bytes [104 127 -1], :rawbytes [104 127 255]}
   {:form "Integer/MIN_VALUE", :value -2147483648, :bytes [117 -128 0 0 0], :rawbytes [117 128 0 0 0]}
   {:form "Integer/MAX_VALUE", :value 2147483647, :bytes [118 127 -1 -1 -1], :rawbytes [118 127 255 255 255]}
   ;;;;min int40
   {:form "(long -549755813887)", :value -549755813887, :bytes [121 -128 0 0 0 1], :rawbytes [121 128 0 0 0 1]}
   ;;; max int40
   {:form "(long 549755813888)", :value 549755813888, :bytes [122 -128 0 0 0 0], :rawbytes [122 128 0 0 0 0]}
   ;;;; max int48
   {:form "(long 1.4073749E14)", :value 140737490000000, :bytes [126 -128 0 0 25 24 -128], :rawbytes [126 128 0 0 25 24 128]}
   ; ;MAX_SAFE_INT                                                                                                         a    b  c   d   e   f   g   h
   {:form "(long  9007199254740991)", :value  9007199254740991, :bytes [-8  0  31 -1 -1 -1 -1 -1 -1],      :rawbytes [248   0  31 255 255 255 255 255 255]}
   ; MAX_SAFE_INT++
   {:form "(long 9007199254740992)", :value 9007199254740992, :bytes [-8 0 32 0 0 0 0 0 0], :rawbytes [248  0  32 0 0 0 0 0 0] :throw? true}
   ;;;;MIN_SAFE_INTEGER
   {:form "(long -9007199254740991)", :value -9007199254740991,       :bytes [-8 -1 -32 0 0 0 0 0 1],  :rawbytes [248 255 224 0 0 0 0 0 1] :throw? false}
   ;;;; MIN_SAFE_INTEGER--
   {:form "(long -9007199254740992)", :value -9007199254740992,       :bytes [-8 -1 -32 0 0 0 0 0 0],  :rawbytes [248 255 224 0 0 0 0 0 0] :throw? true}
   ;;;; MIN_SAFE_INTEGER - 2
   {:form "(long -9007199254740993)", :value -9007199254740993, :bytes [-8 -1 -33 -1 -1 -1 -1 -1 -1],  :rawbytes [248 255 223 255 255 255 255 255 255] :throw? true}
   {:form "Long/MAX_VALUE", :value 9223372036854775807,  :bytes [-8 127 -1 -1 -1 -1 -1 -1 -1], :rawbytes [248 127 255 255 255 255 255 255 255] :throw? true}
   {:form "Long/MIN_VALUE", :value -9223372036854775808, :bytes [-8 -128 0 0 0 0 0 0 0],  :rawbytes [248 128   0 0 0 0 0 0 0] :throw? true}])

(def float-samples
  [{:form "Float/MIN_VALUE", :value 1.4E-45, :bytes [-7 0 0 0 1], :rawbytes [249 0 0 0 1]}
   {:form "Float/MAX_VALUE", :value 3.4028235E38, :bytes [-7 127 127 -1 -1], :rawbytes [249 127 127 255 255]}])

(def double-samples
  [{:form "Double/MIN_VALUE", :value 4.9E-324, :bytes [-6 0 0 0 0 0 0 0 1], :rawbytes [250 0 0 0 0 0 0 0 1]}
   {:form "Double/MAX_VALUE", :value 1.7976931348623157E308, :bytes [-6 127 -17 -1 -1 -1 -1 -1 -1], :rawbytes [250 127 239 255 255 255 255 255 255]}])

(defn local-dir [] (fs/dirname (mac/filename)))
(def path (js/require "path"))

(defonce chunked_bytes_sample
  (let [path (path.join (local-dir) "chunked_bytes_sample.edn")]
    (delay
     (do
       (js/console.info "slurping path:" path)
       (let [_str (slurp path)]
         (js/console.info "reading edn...")
         (read-string _str))))))

(defonce chunked_string_sample
  (let [path (path.join (local-dir) "chunked_string_sample.edn")]
    (delay
     (do
       (js/console.info "slurping path:" path)
       (let [_str (slurp path)]
         (js/console.info "reading edn...")
         (read-string _str))))))

(def utf8-samples
  [{:tag? true :form "(->utf8 \"hello\")", :bytes [-17 -34 117 116 102 56 2 5 104 101 108 108 111], :rawbytes [239 222 117 116 102 56 2 5 104 101 108 108 111], :value "hello"}
   {:tag? true :form "(->utf8 \"😉 😎 🤔 😐 🙄\")", :bytes [-17 -34 117 116 102 56 2 24 -16 -97 -104 -119 32 -16 -97 -104 -114 32 -16 -97 -92 -108 32 -16 -97 -104 -112 32 -16 -97 -103 -124], :rawbytes [239 222 117 116 102 56 2 24 240 159 152 137 32 240 159 152 142 32 240 159 164 148 32 240 159 152 144 32 240 159 153 132], :value "😉 😎 🤔 😐 🙄"}
   {:tag? false :form "(->utf8 \"hello\")", :bytes [-65 5 104 101 108 108 111], :rawbytes [191 5 104 101 108 108 111], :value "hello"}
   {:tag? false
    :form "(->utf8 \"😉 😎 🤔 😐 🙄\")"
    :bytes [-65 24 -16 -97 -104 -119 32 -16 -97 -104 -114 32 -16 -97 -92 -108 32 -16 -97 -104 -112 32 -16 -97 -103 -124]
    :rawbytes [191 24 240 159 152 137 32 240 159 152 142 32 240 159 164 148 32 240 159 152 144 32 240 159 153 132]
    :value "😉 😎 🤔 😐 🙄"}
   {:form "(->utf8 \"I'm a reasonable man, get off my case\")",
    :bytes [-17 -34 117 116 102 56 2 37 73 39 109 32 97 32 114 101 97 115 111 110 97 98 108 101 32 109 97 110 44 32 103 101 116 32 111 102 102 32 109 121 32 99 97 115 101]
    :byte-count 45
    :footer false
    :rawbytes [239 222 117 116 102 56 2 37 73 39 109 32 97 32 114 101 97 115 111 110 97 98 108 101 32 109 97 110 44 32 103 101 116 32 111 102 102 32 109 121 32 99 97 115 101]
    :raw-byte-count 45
    :tag? true
    :value "I'm a reasonable man, get off my case"}
    {:form "(->utf8 \"I'm a reasonable man, get off my case\")"
     :bytes [-65 37 73 39 109 32 97 32 114 101 97 115 111 110 97 98 108 101 32 109 97 110 44 32 103 101 116 32 111 102 102 32 109 121 32 99 97 115 101]
     :byte-count 39
     :footer false
     :rawbytes [191 37 73 39 109 32 97 32 114 101 97 115 111 110 97 98 108 101 32 109 97 110 44 32 103 101 116 32 111 102 102 32 109 121 32 99 97 115 101]
     :raw-byte-count 39
     :tag? false
     :value "I'm a reasonable man, get off my case"}])

(def typed-array-samples
  [{:form "(byte-array [7 11 13 17])", :bytes [-44 7 11 13 17], :rawbytes [212 7 11 13 17], :input [7 11 13 17]}
   {:form "(int-array [7 11 13 17])", :bytes [-77 4 7 11 13 17], :rawbytes [179 4 7 11 13 17], :input [7 11 13 17]}
   {:form "(float-array [7 11 13 17])", :bytes [-76 4 -7 64 -32 0 0 -7 65 48 0 0 -7 65 80 0 0 -7 65 -120 0 0], :rawbytes [180 4 249 64 224 0 0 249 65 48 0 0 249 65 80 0 0 249 65 136 0 0], :input [7 11 13 17]}
   {:form "(double-array [7 11 13 17])", :bytes [-79 4 -6 64 28 0 0 0 0 0 0 -6 64 38 0 0 0 0 0 0 -6 64 42 0 0 0 0 0 0 -6 64 49 0 0 0 0 0 0], :rawbytes [177 4 250 64 28 0 0 0 0 0 0 250 64 38 0 0 0 0 0 0 250 64 42 0 0 0 0 0 0 250 64 49 0 0 0 0 0 0], :input [7 11 13 17]}
   {:form "(long-array [7 11 13 17])", :bytes [-80 4 7 11 13 17], :rawbytes [176 4 7 11 13 17], :input [7 11 13 17]}
   {:form "(object-array [7 11 13 17])", :bytes [-75 4 7 11 13 17], :rawbytes [181 4 7 11 13 17], :input [7 11 13 17]}])

(def inst-samples
  [{:form #inst "2018-05-29T11:17:10.600-00:00" :bytes [-56 123 99 -85 -99 -71 72] :rawbytes [200 123 99 171 157 185 72] :value #inst "2018-05-29T11:17:10.600-00:00"}
   {:form "(java.util.Date.)", :bytes [-56 123 99 -85 -92 -95 -91], :rawbytes [200 123 99 171 164 161 165], :value #inst "2018-05-29T11:24:43.301-00:00"}])

(def misc-samples
  [{:form "[1 2 3]", :value [1 2 3], :bytes [-25 1 2 3], :rawbytes [231 1 2 3]}
   {:form "[true false [nil]]", :value [true false [nil]], :bytes [-25 -11 -10 -27 -9], :rawbytes [231 245 246 229 247]}])

(def uuid-samples
  [{:form "(java.util.UUID/randomUUID)", :bytes [-61 -39 16 15 86 86 -123 61 -80 71 99 -66 80 -20 17 -2 19 83 -81],
    :rawbytes [195 217 16 15 86 86 133 61 176 71 99 190 80 236 17 254 19 83 175],
    :value #uuid "0f565685-3db0-4763-be50-ec11fe1353af"}])

(def footer-samples
  [{:form "(byte-array [7 11 13 17])"
    :bytes [-44 7 11 13 17 -49 -49 -49 -49 0 0 0 5 33 -60 4 70]
    :footer true
    :rawbytes [212 7 11 13 17 207 207 207 207 0 0 0 5 33 196 4 70]
    :input [7 11 13 17]}
   {:form "[1 2 3]",
    :bytes [-25 1 2 3 -49 -49 -49 -49 0 0 0 4 32 36 4 46],
    :footer true,
    :rawbytes [231 1 2 3 207 207 207 207 0 0 0 4 32 36 4 46],
    :value [1 2 3]}])
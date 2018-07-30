(ns fress.samples
  (:require-macros [fress.test-macros :as mac])
  (:require [cljs.tools.reader :refer [read-string]]))

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

(defn byte-samples []
  [{:form "(byte-array [-1 -2 -3 0 1 2 3])"
    :bytes [-41 -1 -2 -3 0 1 2 3]
    :input [-1 -2 -3 0 1 2 3]}
   {:form "(byte-array [-4 -3 -2 -1 0 1 2 3 4])",
    :bytes [-39 9 -4 -3 -2 -1 0 1 2 3 4]
    :input [-4 -3 -2 -1 0 1 2 3 4]}
   (assoc (mac/inline-edn "chunked_bytes_sample.edn")
          :chunked? true
          :input (vec (take 70000 (repeat 99))))])

(defn string-samples []
  [{:form "\"hola\"", :bytes [-34 104 111 108 97], :value "hola"}
   {:form "(apply str (take 20 (repeat \\A)))", :bytes [-29 20 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65 65], :value "AAAAAAAAAAAAAAAAAAAA"}
   {:form "\"😉 😎 🤔 😐 🙄\"",
    :bytes [-29 34 -19 -96 -67 -19 -72 -119 32 -19 -96 -67 -19 -72 -114 32 -19 -96 -66 -19 -76 -108 32 -19 -96 -67 -19 -72 -112 32 -19 -96 -67 -19 -71 -124]
    :value "😉 😎 🤔 😐 🙄"}
   (assoc (mac/inline-edn "chunked_string_sample.edn") :chunked? true)])

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

(def named-samples
  [{:form ":keyword", :bytes [-54 -9 -51 -31 107 101 121 119 111 114 100], :byte-count 11, :footer false, :rawbytes [202 247 205 225 107 101 121 119 111 114 100], :raw-byte-count 11, :value :keyword}
   {:form ":namespaced/keyword", :bytes [-54 -51 -29 10 110 97 109 101 115 112 97 99 101 100 -51 -31 107 101 121 119 111 114 100], :byte-count 23, :footer false, :rawbytes [202 205 227 10 110 97 109 101 115 112 97 99 101 100 205 225 107 101 121 119 111 114 100], :raw-byte-count 23, :value :namespaced/keyword}
   {:form 'foo, :bytes [-55 -9 -51 -35 102 111 111], :byte-count 7, :footer false, :rawbytes [201 247 205 221 102 111 111], :raw-byte-count 7, :value (quote foo)}
   {:form 'foo/bar, :bytes [-55 -51 -35 102 111 111 -51 -35 98 97 114], :byte-count 11, :footer false, :rawbytes [201 205 221 102 111 111 205 221 98 97 114], :raw-byte-count 11, :value (quote foo/bar)}
   {:form 'foo/bar.baz, :bytes [-55 -51 -35 102 111 111 -51 -31 98 97 114 46 98 97 122], :byte-count 15, :footer false, :rawbytes [201 205 221 102 111 111 205 225 98 97 114 46 98 97 122], :raw-byte-count 15, :value (quote foo/bar.baz)}])

(def list-samples
  [{:form "[]", :bytes [-28], :byte-count 1, :footer false, :rawbytes [228], :raw-byte-count 1, :value []}
   {:form "()", :bytes [-28], :byte-count 1, :footer false, :rawbytes [228], :raw-byte-count 1, :value ()}
   {:form "[\"\"]", :bytes [-27 -38], :byte-count 2, :footer false, :rawbytes [229 218], :raw-byte-count 2, :value [""]}
   {:form "[\"\" true false nil]", :bytes [-24 -38 -11 -10 -9], :byte-count 5, :footer false, :rawbytes [232 218 245 246 247], :raw-byte-count 5, :value ["" true false nil]}
   {:form "[\"\" true false nil [-1 0 1]]", :bytes [-23 -38 -11 -10 -9 -25 -1 0 1], :byte-count 9, :footer false, :rawbytes [233 218 245 246 247 231 255 0 1], :raw-byte-count 9, :value ["" true false nil [-1 0 1]]}
   {:form "[\"\" true false nil [-1 0 1 [\"bonjour\"]]]", :bytes [-23 -38 -11 -10 -9 -24 -1 0 1 -27 -31 98 111 110 106 111 117 114], :byte-count 18, :footer false, :rawbytes [233 218 245 246 247 232 255 0 1 229 225 98 111 110 106 111 117 114], :raw-byte-count 18, :value ["" true false nil [-1 0 1 ["bonjour"]]]}])

(def map-samples
  [{:form "{}", :bytes [-64 -28], :byte-count 2, :footer false, :rawbytes [192 228], :raw-byte-count 2, :value {}}
   {:form "{:a nil}", :bytes [-64 -26 -54 -9 -51 -37 97 -9], :byte-count 8, :footer false, :rawbytes [192 230 202 247 205 219 97 247], :raw-byte-count 8, :value {:a nil}}
   {:form "#:foo{:bar {}}", :bytes [-64 -26 -54 -51 -35 102 111 111 -51 -35 98 97 114 -64 -28], :byte-count 15, :footer false, :rawbytes [192 230 202 205 221 102 111 111 205 221 98 97 114 192 228], :raw-byte-count 15, :value #:foo{:bar {}}}
   {:form "{:a 0, :b \"1\", :user/c {[1 2 3] :user/kw}}", :bytes [-64 -22 -54 -9 -51 -37 97 0 -54 -9 -51 -37 98 -37 49 -54 -51 -34 117 115 101 114 -51 -37 99 -64 -26 -25 1 2 3 -54 -126 -51 -36 107 119], :byte-count 37, :footer false, :rawbytes [192 234 202 247 205 219 97 0 202 247 205 219 98 219 49 202 205 222 117 115 101 114 205 219 99 192 230 231 1 2 3 202 130 205 220 107 119], :raw-byte-count 37, :value {:a 0, :b "1", :user/c {[1 2 3] :user/kw}}}])

(def inst-samples
  [{:form #inst "2018-05-29T11:17:10.600-00:00"
    :bytes [-56 123 99 -85 -99 -71 72]
    :rawbytes [200 123 99 171 157 185 72]
    :value #inst "2018-05-29T11:17:10.600-00:00"}
   {:form "(java.util.Date.)",
    :bytes [-56 123 99 -85 -92 -95 -91]
    :rawbytes [200 123 99 171 164 161 165]
    :value #inst "2018-05-29T11:24:43.301-00:00"}])

(def uuid-samples
  [{:form "(java.util.UUID/randomUUID)", :bytes [-61 -39 16 15 86 86 -123 61 -80 71 99 -66 80 -20 17 -2 19 83 -81],
    :rawbytes [195 217 16 15 86 86 133 61 176 71 99 190 80 236 17 254 19 83 175],
    :value #uuid "0f565685-3db0-4763-be50-ec11fe1353af"}])

(def regex-samples
  [{:form "#\"\\n\"", :bytes [-60 -36 92 110], :byte-count 4, :footer false, :rawbytes [196 220 92 110], :raw-byte-count 4, :value #"\n"}])

(def set-samples
  [{:form "#{}", :bytes [-63 -28], :byte-count 2, :footer false, :rawbytes [193 228], :raw-byte-count 2, :value #{}}
   {:form "#{:user/foo}", :bytes [-63 -27 -54 -51 -34 117 115 101 114 -51 -35 102 111 111], :byte-count 14, :footer false, :rawbytes [193 229 202 205 222 117 115 101 114 205 221 102 111 111], :raw-byte-count 14, :value #{:user/foo}}
   {:form "#{nil :user/foo [1 2 3] -99}", :bytes [-63 -24 -9 -54 -51 -34 117 115 101 114 -51 -35 102 111 111 -25 1 2 3 79 -99], :byte-count 21, :footer false, :rawbytes [193 232 247 202 205 222 117 115 101 114 205 221 102 111 111 231 1 2 3 79 157], :raw-byte-count 21, :value #{nil :user/foo [1 2 3] -99}}])

(def misc-samples (concat named-samples list-samples map-samples
                          inst-samples  uuid-samples regex-samples
                          set-samples))

(def uri-samples
  [{:form "(java.net.URI. \"https://www.youtube.com/watch?v=xvhQitzj0zQ\")"
    :bytes [-59 -29 43 104 116 116 112 115 58 47 47 119 119 119 46 121 111 117 116 117 98 101 46 99 111 109 47 119 97 116 99 104 63 118 61 120 118 104 81 105 116 122 106 48 122 81]
    :byte-count 46,
    :footer false
    :input "https://www.youtube.com/watch?v=xvhQitzj0zQ"}
   {:form "(java.net.URI. \"ftp://ftp.is.co.za/rfc/rfc1808.txt\")", :bytes [-59 -29 34 102 116 112 58 47 47 102 116 112 46 105 115 46 99 111 46 122 97 47 114 102 99 47 114 102 99 49 56 48 56 46 116 120 116], :byte-count 37, :footer false, :input "ftp://ftp.is.co.za/rfc/rfc1808.txt"}
   {:form "(java.net.URI. \"http://www.ietf.org/rfc/rfc2396.txt\")", :bytes [-59 -29 35 104 116 116 112 58 47 47 119 119 119 46 105 101 116 102 46 111 114 103 47 114 102 99 47 114 102 99 50 51 57 54 46 116 120 116], :byte-count 38, :footer false, :input "http://www.ietf.org/rfc/rfc2396.txt"}
   {:form "(java.net.URI. \"mailto:John.Doe@example.com\")", :bytes [-59 -29 27 109 97 105 108 116 111 58 74 111 104 110 46 68 111 101 64 101 120 97 109 112 108 101 46 99 111 109], :byte-count 30, :footer false, :input "mailto:John.Doe@example.com"}
   {:form "(java.net.URI. \"news:comp.infosystems.www.servers.unix\")", :bytes [-59 -29 38 110 101 119 115 58 99 111 109 112 46 105 110 102 111 115 121 115 116 101 109 115 46 119 119 119 46 115 101 114 118 101 114 115 46 117 110 105 120], :byte-count 41, :footer false, :input "news:comp.infosystems.www.servers.unix"}
   {:form "(java.net.URI. \"tel:+1-816-555-1212\")", :bytes [-59 -29 19 116 101 108 58 43 49 45 56 49 54 45 53 53 53 45 49 50 49 50], :byte-count 22, :footer false, :input "tel:+1-816-555-1212"}
   {:form "(java.net.URI. \"telnet://192.0.2.16:80/\")", :bytes [-59 -29 23 116 101 108 110 101 116 58 47 47 49 57 50 46 48 46 50 46 49 54 58 56 48 47], :byte-count 26, :footer false, :input "telnet://192.0.2.16:80/"}
   ; "ldap://[2001:db8::7]/c=GB?objectClass?one"
   ; "urn:oasis:names:specification:docbook:dtd:xml:4.1."
   ])

(def typed-array-samples
  [{:form "(byte-array [7 11 13 17])", :bytes [-44 7 11 13 17], :rawbytes [212 7 11 13 17], :input [7 11 13 17]}
   {:form "(int-array [7 11 13 17])", :bytes [-77 4 7 11 13 17], :rawbytes [179 4 7 11 13 17], :input [7 11 13 17]}
   {:form "(float-array [7 11 13 17])", :bytes [-76 4 -7 64 -32 0 0 -7 65 48 0 0 -7 65 80 0 0 -7 65 -120 0 0], :rawbytes [180 4 249 64 224 0 0 249 65 48 0 0 249 65 80 0 0 249 65 136 0 0], :input [7 11 13 17]}
   {:form "(double-array [7 11 13 17])", :bytes [-79 4 -6 64 28 0 0 0 0 0 0 -6 64 38 0 0 0 0 0 0 -6 64 42 0 0 0 0 0 0 -6 64 49 0 0 0 0 0 0], :rawbytes [177 4 250 64 28 0 0 0 0 0 0 250 64 38 0 0 0 0 0 0 250 64 42 0 0 0 0 0 0 250 64 49 0 0 0 0 0 0], :input [7 11 13 17]}
   {:form "(long-array [7 11 13 17])", :bytes [-80 4 7 11 13 17], :rawbytes [176 4 7 11 13 17], :input [7 11 13 17]}
   {:form "(object-array [7 11 13 17])", :bytes [-75 4 7 11 13 17], :rawbytes [181 4 7 11 13 17], :input [7 11 13 17]}
   {:form "(boolean-array [true false true false])", :bytes [-78 4 -11 -10 -11 -10], :byte-count 6, :footer false, :rawbytes [178 4 245 246 245 246], :raw-byte-count 6, :input [true false true false]}])

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

(def cached-sample
  {:bytes [-51 -63 -23 -33 104 101 108 108 111 1 79 -42 -10 -56 123 99 -79 -33 -94 -3 -128]
   :value #{"hello" 1 -42 false #inst "2018-05-30T16:26:53.565-00:00"}})

(def record-sample
  {:bytes [-17 -32 114 101 99 111 114 100 2 -51 -55 -9 -51 -29 14 102 114 101 115 115 46 97 112 105 46 66 111 111 107 -64 -19 -51 -54 -9 -51 -32 97 117 116 104 111 114 -32 66 111 114 103 101 115 -51 -54 -9 -51 -33 116 105 116 108 101 -29 38 69 108 32 106 97 114 100 -61 -83 110 32 100 101 32 115 101 110 100 101 114 111 115 32 113 117 101 32 115 101 32 98 105 102 117 114 99 97 110 -3]
   :author "Borges"
   :title "El jardín de senderos que se bifurcan"
   :class-sym 'fress.api.Book})

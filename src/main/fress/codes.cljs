(ns fress.codes)

(def ^:const PRIORITY_CACHE_PACKED_START 0x80)
(def ^:const PRIORITY_CACHE_PACKED_END 0xA0)
(def ^:const STRUCT_CACHE_PACKED_START 0xA0)
(def ^:const STRUCT_CACHE_PACKED_END 0xB0)
(def ^:const LONG_ARRAY 0xB0)
(def ^:const DOUBLE_ARRAY 0xB1)
(def ^:const BOOLEAN_ARRAY 0xB2)
(def ^:const INT_ARRAY 0xB3)
(def ^:const FLOAT_ARRAY 0xB4)
(def ^:const OBJECT_ARRAY 0xB5)
(def ^:const MAP 0xC0)
(def ^:const SET 0xC1)
(def ^:const _UUID 0xC3)
(def ^:const REGEX 0xC4)
(def ^:const URI 0xC5)
(def ^:const BIGINT 0xC6)
(def ^:const BIGDEC 0xC7)
(def ^:const INST 0xC8)
(def ^:const SYM 0xC9)
(def ^:const KEY 0xCA)
(def ^:const GET_PRIORITY_CACHE 0xCC)
(def ^:const PUT_PRIORITY_CACHE 0xCD)
(def ^:const PRECACHE 0xCE)
(def ^:const FOOTER 0xCF) ;207
(def ^:const FOOTER_MAGIC 0xCFCFCFCF) ;3486502863
(def ^:const BYTES_PACKED_LENGTH_START 0xD0) ;208
(def ^:const BYTES_PACKED_LENGTH_END   0xD8) ;216
(def ^:const BYTES_CHUNK 0xD8) ;216
(def ^:const BYTES 0xD9) ;217
(def ^:const STRING_PACKED_LENGTH_START 0xDA) ;218
(def ^:const STRING_PACKED_LENGTH_END 0xE2) ;226
(def ^:const STRING_CHUNK 0xE2) ;226
(def ^:const STRING 0xE3) ;227
(def ^:const LIST_PACKED_LENGTH_START 0xE4) ; 228
(def ^:const LIST_PACKED_LENGTH_END 0xEC) ; 236
(def ^:const LIST 0xEC) ;236
(def ^:const BEGIN_CLOSED_LIST 0xED) ; 237
(def ^:const BEGIN_OPEN_LIST 0xEE) ; 238
(def ^:const STRUCTTYPE 0xEF)
(def ^:const STRUCT 0xF0 );240
(def ^:const META 0xF1)
(def ^:const ANY 0xF4)
(def ^:const TRUE 0xF5)
(def ^:const FALSE 0xF6)
(def ^:const NULL 0xF7 );247
(def ^:const INT 0xF8 );248
(def ^:const FLOAT 0xF9 );249
(def ^:const DOUBLE 0xFA ); 250
(def ^:const DOUBLE_0 0xFB );251
(def ^:const DOUBLE_1 0xFC );252
(def ^:const END_COLLECTION 0xFD ); 253
(def ^:const RESET_CACHES 0xFE ); 254
(def ^:const INT_PACKED_1_START 0xFF );255
(def ^:const INT_PACKED_1_END 0x40 ); 64
(def ^:const INT_PACKED_2_START 0x40)
(def ^:const INT_PACKED_2_ZERO 0x50 ); 80
(def ^:const INT_PACKED_2_END 0x60)
(def ^:const INT_PACKED_3_START 0x60)
(def ^:const INT_PACKED_3_ZERO 0x68) ;104
(def ^:const INT_PACKED_3_END 0x70) ;112
(def ^:const INT_PACKED_4_START 0x70)
(def ^:const INT_PACKED_4_ZERO 0x72)
(def ^:const INT_PACKED_4_END 0x74)
(def ^:const INT_PACKED_5_START 0x74) ; 116
(def ^:const INT_PACKED_5_ZERO 0x76) ; 118
(def ^:const INT_PACKED_5_END 0x78) ; 120
(def ^:const INT_PACKED_6_START 0x78) ;120
(def ^:const INT_PACKED_6_ZERO 0x7A) ; 122
(def ^:const INT_PACKED_6_END 0x7C) ; 124
(def ^:const INT_PACKED_7_START 0x7C)
(def ^:const INT_PACKED_7_ZERO 0x7E)
(def ^:const INT_PACKED_7_END 0x80)


(def tag->code
  { "map"      MAP
    "set"      SET
    "uuid"     _UUID
    "regex"    REGEX
    "uri"      URI
    "bigint"   BIGINT
    "bigdec"   BIGDEC
    "inst"     INST
    "sym"      SYM
    "key"      KEY
    "int[]"    INT_ARRAY
    "float[]"  FLOAT_ARRAY
    "double[]" DOUBLE_ARRAY
    "long[]"   LONG_ARRAY
    "boolean[]" BOOLEAN_ARRAY
    "Object[]" OBJECT_ARRAY})
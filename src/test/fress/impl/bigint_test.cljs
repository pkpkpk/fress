(ns fress.impl.bigint-test
  (:require [cljs.test :refer-macros [deftest is are testing async]]
            [fress.impl.bigint :as bn :refer [bigint]]
            [fress.util :refer [u8-array] :as util]
            [fress.test-helpers :as helpers :refer [log is= seq= are-nums= float=]]))

(deftest bn64-bit-switch-test
  (are [a b] (== a b)
        1 (bn/bit-switch util/i64_MIN_VALUE)
        8 (bn/bit-switch (bigint (- (Math/pow 2 56))))
       16 (bn/bit-switch (bigint (- (Math/pow 2 48))))
       24 (bn/bit-switch (bigint (- (Math/pow 2 40))))
       32 (bn/bit-switch (bigint (- (Math/pow 2 32))))
       40 (bn/bit-switch (bigint -16777216))
       52 (bn/bit-switch (bigint -4096))
       55 (bn/bit-switch (bigint -257))
       56 (bn/bit-switch (bigint -256))
       56 (bn/bit-switch (bigint -129))
       57 (bn/bit-switch (bigint -128))
       58 (bn/bit-switch (bigint -64))
       59 (bn/bit-switch (bigint -32))
       64 (bn/bit-switch (bigint 0))
       58 (bn/bit-switch (bigint 32))
       57 (bn/bit-switch (bigint 64))
       56 (bn/bit-switch (bigint 128))
       56 (bn/bit-switch (bigint 255))
       55 (bn/bit-switch (bigint 256))
       51 (bn/bit-switch (bigint 4096))
       39 (bn/bit-switch (bigint 16777216))
       31 (bn/bit-switch (bigint (Math/pow 2 32)))
       23 (bn/bit-switch (bigint (Math/pow 2 40)))
       15 (bn/bit-switch (bigint (Math/pow 2 48)))
        7 (bn/bit-switch (bigint (Math/pow 2 56)))
        1 (bn/bit-switch util/i64_MAX_VALUE)))


(deftest bitshift-test
  (is (= 0 (bit-shift-right 1 1) (js/Number (bn/>> (bigint 1) 1))))
  (is (= 1 (bit-shift-right 2 1) (js/Number (bn/>> (bigint 2) 1))))
  (is (= 2 (bit-shift-right 4 1) (js/Number (bn/>> (bigint 4) 1))))
  (is (= -2 (bit-shift-right -4 1) (js/Number (bn/>> (bigint -4) 1)))))

(ns fress.impl.hopmap-test
  (:require-macros [fress.macros :refer [<<]])
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.test-helpers :refer [is=]]
            [fress.impl.hopmap :as hopmap]))

(defn log [& args] (.apply js/console.log js/console (into-array args)))

(deftest intern-test
  (let [hop (hopmap/hopmap 10)]
    (is= (.-count hop) 0)
    (let [value "foo"
          old-count (.-count hop)
          hash (hopmap/_hash value)
          mask (dec (.-cap hop))
          bkt (bit-and hash mask)]
      (is (zero? (aget (.-hopidx hop) (<< bkt 2))))
      (let [idx (hopmap/intern hop value)]
        (is= (.-count hop) 1)
        (is (not (zero? (aget (.-hopidx hop) (<< bkt 2)))))
        (is= (aget (.-hopidx hop) 0) hash)
        (is= (aget (.-hopidx hop) 1) old-count 0)
        (is= (aget (.-keys hop) idx) value)
        (testing "try to intern same value"
          (let [idx2 (hopmap/intern hop value)]
            (is= (.-count hop) 1)
            (is= idx idx2))))))
  (let [hop (hopmap/hopmap 100)
        v_38 "38"
        i_38 (hopmap/intern hop v_38)
        v_39 "39"
        i_39 (hopmap/intern hop v_39)]
    (is= i_38 (get hop v_38))
    (is= i_39 (get hop v_39))))

(deftest hopmap-test
  (let [n 50
        stuff (array-list)
        _ (dotimes [i n]
            (.add stuff (str i)))
        hop (hopmap/hopmap 1024)
        ht (atom {})]
    (dotimes [i n]
      (let [_str (.get stuff i)]
        (binding [hopmap/*debug* (if (== i 39) true)]
          (let [i (hopmap/intern hop _str)]
            ; (log "_str:" _str "i:" i)
            (is (= i (hopmap/intern hop _str))  "interning same value should return same index")))
        (swap! ht assoc _str i)))
    (dotimes [i n]
     (let [s (.get stuff i)]
       (is (=  i (get @ht s) (get hop s)) (str "i: " (pr-str i)))))
    (is= -1 (get hop "foobar"))))
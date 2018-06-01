(ns fress.impl.hopmap-test
  (:require-macros [fress.macros :refer [<<]])
  (:require [cljs.test :refer-macros [deftest is testing]]
            [fress.test-helpers :refer [is=]]
            [fress.impl.hopmap :as hopmap]
            [fress.util :as util]))

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
            (is= idx idx2)))))))

;; (bit-and 1023 (hash "70")) (bit-and 1023 (hash "39"))
(deftest hopmap-test
  (let [n 200
        stuff (array-list)
        _ (dotimes [i n]
            (.add stuff (str i)))
        hop (hopmap/hopmap 1024)
        ht (atom {})]
    (dotimes [i n]
      (let [_str (.get stuff i)]
        (binding [util/*debug* false]
          (let [i (hopmap/intern hop _str)]
            (is (= i (hopmap/intern hop _str))  "interning same value should return same index")
            (is (= i (hopmap/oldIndex hop _str)))))
        (swap! ht assoc _str i)))
    (dotimes [i n]
     (let [s (.get stuff i)]
       (binding [util/*debug* false]
         (is (=  i (get @ht s) (get hop s)) (str "i: " (pr-str i))))))
    (is= -1 (get hop "foobar"))))
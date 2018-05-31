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
          bkt (bit-and hash mask)
          ; _(log "bhash before: " (aget (.-hopidx hop) (<< bkt 2)))
          idx (hopmap/intern hop value)
          ; _(log "bhash after: " (aget (.-hopidx hop) (<< bkt 2)))
          ; _(log "hop0" hop)
          ]
      (is= (.-count hop) 1)
      (is= (aget (.-hopidx hop) 0) hash)
      (is= (aget (.-hopidx hop) 1) old-count 0)
      (is= (aget (.-keys hop) idx) value)
      (testing "try to intern same value"
        (let [idx2 (hopmap/intern hop value)]
          (is= (.-count hop) 1)
          (is= idx idx2))))))

; (deftest hopmap-test
;   (let [n 1
;         stuff (array-list)
;         _ (dotimes [i n]
;             (.add stuff (str i)))
;         start (js/performance.now)
;         hop (hopmap/hopmap 100)
;         ht (atom {})]
;     (dotimes [i n]
;       (let [_str (.get stuff i)]
;         (hopmap/intern hop _str)
;         (swap! ht assoc _str i)))
;     (js/console.log hop)
;     (dotimes [i n]
;       (let [s (.get stuff i)]
;         (assert (string? s))
;         (is= i (get @ht s) (get hop s))))
;
;     (is= -1 (get hop "foobar"))))
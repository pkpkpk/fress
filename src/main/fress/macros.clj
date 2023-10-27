(ns fress.macros
  (:require [cljs.env :as env]
            [cljs.analyzer :as ana]
            [cljs.analyzer.api :as ana-api]
            [cljs.util :as util]))

(defmacro >>> [a b]
  `(~'.floor ~'js/Math (/ ~a (~'.pow ~'js/Math 2 ~b))))

(defmacro << [a b]
  `(bit-shift-left ~a ~b))

(defmacro nodejs? [] (= :nodejs (get-in @env/*compiler* [:options :target])))
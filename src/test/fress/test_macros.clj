(ns fress.test-macros
  (:require [cljs.env :as env]
            [cljs.analyzer :as ana]
            [cljs.analyzer.api :as ana-api]
            [cljs.util :as util]))

(defmacro output-dir []
  (let [{:keys [output-dir]} (ana-api/get-options)]
    (or output-dir "out")))

(defmacro output-to []
  (let [{:keys [output-to]} (ana-api/get-options)]
    output-to))

(defmacro current-ns
  "returns namespace symbol of caller"
  [] `(quote  ~ana/*cljs-ns*))

(defmacro filename
  "returns filename of caller."
  [] ana/*cljs-file*)

(defmacro target [] (get-in @env/*compiler* [:options :target]))

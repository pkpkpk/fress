(ns fress.test-macros
  (:require [cljs.env :as env]
            [cljs.analyzer :as ana]
            [cljs.analyzer.api :as ana-api]
            [cljs.util :as util]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

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

(defmacro inline-edn [filename]
  (let [file (io/file (.getParent (io/file ana/*cljs-file*)) filename)
        _ (assert (.exists file))
        data (edn/read-string (slurp file))]
    data))

(defmacro root
  "get project-root of caller. This makes things friendly for 'checkout' compilations"
  []
  (let [caller-file (io/file ana/*cljs-file*)]
    (loop [f caller-file]
      (if (= "src" (.getName f))
        (if-let [root (.getParentFile f)] ;; no clue why this doesnt work when hits cwd
          (.getPath root)
          (System/getProperty "user.dir"))
        (recur (.getParentFile f))))))
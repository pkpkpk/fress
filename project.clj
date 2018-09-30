;  https://clojureverse.org/t/combining-tools-deps-with-leiningen/658/4
(require '[clojure.edn :as edn])

; {foo {:mvn/verion "string"}
;  bar {:mvn/version "string"}... } => [[foo "string" :exculsions [...]] ...]
(defn deps->vec [deps]
  (into []
    (map
      (fn [[dep {:keys [:mvn/version exclusions]}]]
        (cond-> [dep version]
          exclusions (conj :exclusions exclusions))))
    deps))

(def raw-deps (edn/read-string (slurp "deps.edn")))
(def config   (edn/read-string (slurp "config.edn")))

(let [proj-deps (deps->vec (get raw-deps :deps))
      src-paths (vec (get raw-deps :paths))
      test-paths (get-in raw-deps [:aliases :test :extra-paths])
      dev-deps (deps->vec (get-in raw-deps [:aliases :dev :extra-deps]))
      profiles (for [[alias-key {:keys [builds]}] (get config :aliases)]
                 (when (some? builds)
                   (let [deps (get-in raw-deps [:aliases alias-key :extra-deps])]
                     [alias-key {:dependencies (deps->vec deps)
                                 :cljsbuild {:builds builds}}])))]
  (defproject fress "0.3.0"
    :description "Fressian for clojure(script) and WASM"
    :url "https://github.com/pkpkpk/fress"
    :repositories [["clojars" {:sign-releases false}]]
    :license {:name "Eclipse Public License"
              :url "http://www.eclipse.org/legal/epl-v10.html"}
    :plugins [[lein-cljsbuild "1.1.7"]]
    :jar-exclusions [#"^wasm-test/.*|public/.*"]
    :dependencies ~proj-deps
    :source-paths ~src-paths
    :test-paths ~test-paths
    :aot ~(get config :aot)
    :profiles ~(into {} profiles)))


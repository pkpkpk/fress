;  https://clojureverse.org/t/combining-tools-deps-with-leiningen/658/4
(require '[clojure.edn :as edn])

(defn deps->vec [deps]
  (into []
    (map
      (fn [[dep {:keys [:mvn/version exclusions]}]]
        (cond-> [dep version]
          exclusions (conj :exclusions exclusions))))
    deps))

(let [raw-deps (edn/read-string (slurp "deps.edn"))
      proj-deps (deps->vec (get raw-deps :deps))
      src-paths (vec (get raw-deps :paths))
      test-paths (get-in raw-deps [:aliases :test :extra-paths])
      dev-deps (deps->vec (get-in raw-deps [:aliases :dev :extra-deps]))
      config (edn/read-string (slurp "config.edn"))]

  (defproject fress "0.1.0"
    :dependencies ~proj-deps
    :source-paths ~src-paths
    :test-paths ~test-paths
    :aot ~(get config :aot)))


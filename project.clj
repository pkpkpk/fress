(defproject fress "0.3.3"
  :description "Fressian for clojure(script) and WASM"
  :url "https://github.com/pkpkpk/fress"
  :repositories [["clojars" {:sign-releases false}]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :source-paths ["src/main/clj" "src/main/cljs"]
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/data.fressian "1.0.0"]]
  :jar-exclusions [#"^wasm-test/.*|public/.*"]
  :aot [fress.impl.bytestream])

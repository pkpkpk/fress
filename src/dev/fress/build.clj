(ns fress.build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint]]
            [cljs.build.api :as api]
            [cljs.util]))

(def lib 'com.github.pkpkpk/fress)
(def version (format "0.4.%s" (b/git-count-revs nil)))
(def basis (b/create-basis {:project "deps.edn"}))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean
  "Clean target directory."
  ([] (clean {}))
  ([_opts] (b/delete {:path "target"})))

(defn jar
  "Build the library JAR."
  ([] (jar {}))
  ([_opts]
   (b/copy-dir {:src-dirs ["src/main"]
                :target-dir class-dir})
   (b/write-pom {:class-dir class-dir
                 :lib lib
                 :version version
                 :basis basis
                 :src-dirs ["src/main"]
                 :pom-data [[:licenses
                             [:license
                              [:name "Eclipse Public License"]
                              [:url "http://www.eclipse.org/legal/epl-v10.html"]]]]})
   (b/compile-clj {:basis basis
                   :ns-compile '[fress.impl.bytestream]
                   :class-dir class-dir})
   (io/make-parents (io/file "target/deps/deps.cljs"))
   (spit (io/file "target/classes/deps.cljs") (slurp (io/file "src" "deps.cljs")))
   (b/jar {:class-dir class-dir
           :lib lib
           :version version
           :basis basis
           :jar-file jar-file
           :src-dirs ["src/main"]})))

(defn install
  "Install JAR locally."
  ([] (install {}))
  ([_opts]
   (b/install
    {:class-dir class-dir
     :lib lib
     :version version
     :basis basis
     :jar-file jar-file})))

(defn deploy
  "Deploy the JAR to Clojars."
  ([] (deploy {}))
  ([_opts]
   (dd/deploy
    {:artifact (b/resolve-path jar-file)
     :installer :remote
     :pom-file (b/pom-path {:lib lib :class-dir class-dir})})))

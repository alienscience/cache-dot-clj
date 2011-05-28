(defproject clj-cache-masai "0.0.4-SNAPSHOT"
  :description "Masai support for cache-dot-clj."
  :dependencies [[org.clojars.raynes/masai "0.5.1-SNAPSHOT"]
                 [clj-cache "0.0.4-SNAPSHOT"]
                 [cereal "0.1.1"]]
  :dev-dependencies [[org.clojure/clojure "1.2.0"]
                     [org.clojars.raynes/jedis "2.0.0-SNAPSHOT"]
                     [tokyocabinet "1.24.1-SNAPSHOT"]]
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repos
            :comments "same as Clojure"})
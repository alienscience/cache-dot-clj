(defproject uk.org.alienscience/jedis-dot-clj "0.0.3"
  :description "Redis support for cache-dot-clj."
  :dependencies [[redis.clients/jedis "1.5.1"]
                 [uk.org.alienscience/cache-dot-clj "0.0.3"]]
  :dev-dependencies [[org.clojure/clojure "1.2.0"]
                     [org.clojure/clojure-contrib "1.2.0"]
                     [swank-clojure "1.2.1"]]
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"})


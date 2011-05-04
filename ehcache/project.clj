(defproject uk.org.alienscience/ehcache-dot-clj "0.0.3"
  :description "Ehcache support for cache-dot-clj."
  :dependencies [[net.sf.ehcache/ehcache-core "2.2.0"]
                 [uk.org.alienscience/cache-dot-clj "0.0.3"]]
  :dev-dependencies [[org.clojure/clojure "1.2.0"]
                     [org.clojure/clojure-contrib "1.2.0"]
                     [swank-clojure "1.2.1"]
                     [org.slf4j/slf4j-simple "1.5.11"]
                     [clj-file-utils  "0.2.1"]]
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"})


(defproject uk.org.alienscience/ehcache-dot-clj "0.0.4-SNAPSHOT"
  :description "Ehcache support for cache-dot-clj."
  :dependencies [[net.sf.ehcache/ehcache-core "2.4.2"]
                 [uk.org.alienscience/cache-dot-clj "0.0.4-SNAPSHOT"]]
  :dev-dependencies [[org.clojure/clojure "1.2.0"]
                     [org.clojure/clojure-contrib "1.2.0"]
                     [swank-clojure "1.2.1"]
                     [log4j/log4j "1.2.13"]
                     [org.slf4j/slf4j-log4j12 "1.6.1"]
                     [clj-file-utils  "0.2.1"]]
  :warn-on-reflection false
  :jar-exclusions [#"log4j\.properties"]
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"})


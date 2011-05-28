(ns clj-cache.masai-test
  (:use clojure.test
        clj-cache.cache)
  (:require [clj-cache.masai :as masai]))

(defn slow [a] (Thread/sleep a) a)

(def fast-default (cached slow (masai/strategy)))

(defmacro how-long [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         msecs# (/ (double (- (. System (nanoTime)) start#))
                   1000000.0)]
     {:msecs msecs# :ret ret#}))

(defn expect [situation f check t should]
  (let [{:keys [msecs ret]} (how-long (f t))]
    (is (check msecs t) (str situation " (expected time:" t ", actual time: " msecs ") "  should))
    (is (= ret t) (str situation " returns correct value"))))

(defn is-caching [f t]
  (invalidate-cache f t)
  (expect "First call" f > t "hits function" )
  (expect "Second call" f < t "is cached")
  (expect "Third call" f < t "is cached"))

(deftest is-caching-ehcache (is-caching fast-default 100))

(defn invalidating [f t1 t2 t3]
  (invalidate-cache f t1)
  (invalidate-cache f t2)
  (invalidate-cache f t3)
  (expect "First call" f > t1 "hits function")
  (expect "First call" f > t2 "hits function")
  (expect "First call" f > t3 "hits function")
  (invalidate-cache f t1)
  (expect "Invalidated entry" f > t1 "hits function")
  (expect "Second call" f < t2 "is cached")
  (expect "Second call" f < t3 "is cached")
  (expect "Third call" f < t1 "is cached"))

(deftest invalidating-ehcache (invalidating fast-default 50 51 52))

(defn-cached cached-fn
  (masai/strategy)
  "A cached function definition"
  [t]
  (Thread/sleep t)
  t)

(deftest is-caching-def (is-caching cached-fn 100))

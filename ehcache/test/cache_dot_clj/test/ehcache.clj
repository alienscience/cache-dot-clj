
(ns cache-dot-clj.test.ehcache
  "Ehcache tests"
  (:use clojure.test)
  (:use cache-dot-clj.cache)
  (:require [cache-dot-clj.ehcache :as ehcache])
  (:use [clojure.set :only [union]]))

;;--- Copy and paste of cache-dot-clj.test.cache (different src tree)

(defn slow [a] (Thread/sleep a) a)

;; Default ehcache setup has a ttl of 120 secs but unittests should
;; not be affected by this
(def fast-default (cached slow (ehcache/strategy)))

(defmacro how-long [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         msecs# (/ (double (- (. System (nanoTime)) start#))
                   1000000.0)]
     {:msecs msecs# :ret ret#}))

(defn expect [situation f check t should]
  (let [{:keys [msecs ret]} (how-long (f t))]
    (is (check msecs t) (str situation " (" t ") " should))
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
  (ehcache/strategy)
  "A cached function definition"
  [t]
  (Thread/sleep t)
  t)

(deftest is-caching-def (is-caching cached-fn 100))

;; A CacheManager config to use for persistence tests
(def persistent-config
     [:ehcache
      [:disk-store 
       {:path "java.io.tmpdir/ehcache"}]
      [:default-cache
       {:max-elements-in-memory 100
        :eternal false
        :overflow-to-disk true
        :disk-persistent false
        :memory-store-eviction-policy "LRU"}]])

;; TODO: fix null pointer exception 
(deftest is-persistent
  (let [first-manager (ehcache/new-manager persistent-config)
        f (cached slow (ehcache/strategy 
                        first-manager 
                        {:max-elements-in-memory 100
                         :eternal true
                         :overflow-to-disk true
                         :disk-persistent true
                         :clear-on-flush false}))]
    (expect "First call" f > 100 "hits function")
    (expect "Second call" f > 101 "hits function")
    (expect "Third call" f > 102 "hits function") 
    ;; Simulate end of VM
    (ehcache/shutdown first-manager))
  ;; Simulate start of new of VM
  (let [second-manager (ehcache/new-manager persistent-config)
        f (cached slow (ehcache/strategy
                        second-manager
                        {:max-elements-in-memory 100
                         :eternal true
                         :overflow-to-disk true
                         :disk-persistent true
                         :clear-on-flush false}))]
    (expect "First call" f < 100 "is cached")
    (expect "First call" f < 101 "is cached")
    (expect "First call" f < 102 "is cached")
    (ehcache/shutdown second-manager)))

(deftest cache-names
  (let [expected #{"cache-dot-clj.test.ehcache.slow"
                   "cache-dot-clj.test.ehcache.cached-fn"
                   "cache-dot-clj.test.ehcache.persistent-fn"}]
    (is (= (union (set (ehcache/cache-seq)) expected)
           expected))))


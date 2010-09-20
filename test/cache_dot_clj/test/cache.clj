(ns cache-dot-clj.test.cache
  "Resettable memoize tests"
  (:use clojure.test)
  (:use cache-dot-clj.cache))

(defn slow [a] (Thread/sleep a) a)
(def fast-naive (cached slow naive-strategy))
(def fast-lru (cached slow (lru-cache-strategy 3)))
(def fast-ttl (cached slow (ttl-cache-strategy 1000)))
(def fast-lu (cached slow (lu-cache-strategy 3)))
(def fast-external (cached slow naive-external-strategy))

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

(deftest is-caching-naive (is-caching fast-naive 100))
(deftest is-caching-lru (is-caching fast-lru 100))
(deftest is-caching-ttl (is-caching fast-ttl 100))
(deftest is-caching-lu (is-caching fast-lu 100))
(deftest is-caching-external (is-caching fast-external 100))

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
  
(deftest invalidating-naive (invalidating fast-naive 50 51 52))
(deftest invalidating-lru (invalidating fast-lru 50 51 52))
(deftest invalidating-ttl (invalidating fast-ttl 50 51 52))
(deftest invalidating-external (invalidating fast-external 50 51 52))

;; Highly used cache entries from previous tests live on -
;;   use a new cache for LU testing
(deftest invalidating-lu
  (invalidating (cached slow (lu-cache-strategy 3)) 50 51 52))

(deftest overflow-lru
  (invalidate-cache fast-lru 100)
  (invalidate-cache fast-lru 200)
  (invalidate-cache fast-lru 300)
  (invalidate-cache fast-lru 400)
  (expect "First call" fast-lru > 100 "hits function")
  (expect "First call" fast-lru > 200 "hits function")
  (expect "First call" fast-lru > 300 "hits function")
  (expect "First call" fast-lru > 400 "hits function")
  (expect "Second call" fast-lru < 200 "is cached")
  (expect "Second call" fast-lru < 300 "is cached")
  (expect "Second call" fast-lru < 400 "is cached")
  (expect "Function" fast-lru > 100 "removed from cache")
  (expect "Third call" fast-lru < 300 "is cached")
  (expect "Second call" fast-lru < 100 "is cached"))
  
(deftest expire-ttl
  (invalidate-cache fast-ttl 50)
  (invalidate-cache fast-ttl 500)
  (invalidate-cache fast-ttl 600)
  (expect "First call" fast-ttl > 500 "hits function")
  (expect "First call" fast-ttl > 600 "hits function")
  (expect "Second call" fast-ttl > 500 "hits function")
  (expect "First call" fast-ttl > 50 "hits function")
  (expect "Second call" fast-ttl > 600 "hits function")
  (expect "Second call" fast-ttl < 50 "is cached"))

(deftest overflow-lu
  (let [f (cached slow (lu-cache-strategy 3))]
    (expect "First call" f > 50 "hits function")
    (expect "Second call" f < 50 "is cached")
    (expect "First call" f > 51 "hits function")
    (expect "Second call" f < 51 "is cached")
    (expect "First call" f > 52 "hits function")
    (expect "Second call" f < 52 "is cached")
    (expect "First call" f > 53 "hits function")
    (expect "First call" f > 54 "hits function")
    (expect "Second call" f < 54 "is cached")
    (expect "Second call" f > 53 "hits function")))
    

(defn-cached cached-fn
  (lru-cache-strategy 3)
  "A cached function definition"
  [t]
  (Thread/sleep t)
  t)

(deftest is-caching-def (is-caching cached-fn 100))


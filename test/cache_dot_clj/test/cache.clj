(ns cache-dot-clj.test.cache
  "Resettable memoize tests"
  (:use clojure.test)
  (:use cache-dot-clj.cache))

(defn slow [a] (Thread/sleep a) a)
(def fast-naive (cached slow naive-strategy))
(def fast-lru (cached slow (lru-cache-strategy 3)))
(def fast-ttl (cached slow (ttl-cache-strategy 1000)))
(def fast-lu (cached slow (lu-cache-strategy 3)))

(defmacro how-long [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         msecs# (/ (double (- (. System (nanoTime)) start#))
                   1000000.0)]
     {:msecs msecs# :ret ret#}))

  
(defn is-caching [f t]
  (invalidate-cache f t)
  (is (> (:msecs (how-long (f t))) t)
      "First call hits function")
  (is (< (:msecs (how-long (f t))) t)
      "Second call is cached")
  (is (< (:msecs (how-long (f t))) t)
      "Third call is cached"))

(deftest is-caching-naive (is-caching fast-naive 100))
(deftest is-caching-lru (is-caching fast-lru 100))
(deftest is-caching-ttl (is-caching fast-ttl 100))
(deftest is-caching-lu (is-caching fast-lu 100))

(defn is-returning [f t]
  (invalidate-cache f t)
  (is (= (:ret (how-long (f t))) t)
      "First call return value")
  (is (= (:ret (how-long (f t))) t)
      "Second call return value"))

(deftest is-returning-naive (is-returning fast-naive 10))
(deftest is-returning-lru (is-returning fast-lru 10))
(deftest is-returning-ttl (is-returning fast-ttl 10))
(deftest is-returning-lu (is-returning fast-lu 10))

(defn invalidating [f t1 t2 t3]
  (invalidate-cache f t1)
  (invalidate-cache f t2)
  (invalidate-cache f t3)
  (is (> (:msecs (how-long (f t1))) t1)
      "First call hits function")
  (is (> (:msecs (how-long (f t2))) t2)
      "First call hits function")
  (is (> (:msecs (how-long (f t3))) t3)
      "First call hits function")
  (invalidate-cache f t1)
  (is (> (:msecs (how-long (f t1))) t1)
      "Invalidated entry hits function")
  (is (< (:msecs (how-long (f t2))) t2)
      "Second call is cached")
  (is (< (:msecs (how-long (f t3))) t3)
      "Second call is cached")
  (is (< (:msecs (how-long (f t1))) t1)
      "Third call is cached"))
  
(deftest invalidating-naive (invalidating fast-naive 50 51 52))
(deftest invalidating-lru (invalidating fast-lru 50 51 52))
(deftest invalidating-ttl (invalidating fast-ttl 50 51 52))

;; Highly used cache entries from previous tests live on -
;;   use a new cache for LU testing
(deftest invalidating-lu
  (invalidating (cached slow (lu-cache-strategy 3) 50 51 52)))

(deftest overflow-lru
  (invalidate-cache fast-lru 100)
  (invalidate-cache fast-lru 200)
  (invalidate-cache fast-lru 300)
  (invalidate-cache fast-lru 400)
  (is (> (:msecs (how-long (fast-lru 100))) 100)
      "First call hits function")
  (is (> (:msecs (how-long (fast-lru 200))) 200)
      "First call hits function")
  (is (> (:msecs (how-long (fast-lru 300))) 300)
      "First call hits function")
  (is (> (:msecs (how-long (fast-lru 400))) 400)
      "First call hits function")
  (is (< (:msecs (how-long (fast-lru 200))) 200)
      "Second call is cached")
  (is (< (:msecs (how-long (fast-lru 300))) 300)
      "Second call is cached")
  (is (< (:msecs (how-long (fast-lru 400))) 400)
      "Second call is cached")
  (is (> (:msecs (how-long (fast-lru 100))) 100)
      "Function removed from cache")
  (is (< (:msecs (how-long (fast-lru 300))) 300)
      "Third call is cached")
  (is (< (:msecs (how-long (fast-lru 100))) 100)
      "Second call is cached"))
  
(deftest expire-ttl
  (invalidate-cache fast-ttl 50)
  (invalidate-cache fast-ttl 500)
  (invalidate-cache fast-ttl 600)
  (is (> (:msecs (how-long (fast-ttl 500))) 500)
      "First call hits function")
  (is (> (:msecs (how-long (fast-ttl 600))) 600)
      "First call hits function")
  (is (> (:msecs (how-long (fast-ttl 500))) 500)
      "Second call hits function")
  (is (> (:msecs (how-long (fast-ttl 50))) 50)
      "First call hits function")
  (is (> (:msecs (how-long (fast-ttl 600))) 600)
      "Second call hits function")
  (is (< (:msecs (how-long (fast-ttl 50))) 50)
      "Second call is cached"))

(deftest overflow-lu
  (let [f (cached slow (lu-cache-strategy 3))]
    (is (> (:msecs (how-long (f 50))) 50)
        "First call hits function")
    (is (< (:msecs (how-long (f 50))) 50)
        "Second call is cached")
    (is (> (:msecs (how-long (f 51))) 51)
        "First call hits function")
    (is (< (:msecs (how-long (f 51))) 51)
        "Second call is cached")
    (is (> (:msecs (how-long (f 52))) 52)
        "First call hits function")
    (is (< (:msecs (how-long (f 52))) 52)
        "Second call is cached")
    (is (> (:msecs (how-long (f 53))) 53)
        "First call hits function")
    (is (> (:msecs (how-long (f 54))) 54)
        "First call hits function")
    (is (< (:msecs (how-long (f 54))) 54)
        "Second call is cached")
    (is (> (:msecs (how-long (f 53))) 53)
        "Second call hits function")))
    
    

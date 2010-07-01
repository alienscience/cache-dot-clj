(ns cache-dot-clj.test.cache
  "Resettable memoize tests"
  (:use clojure.test)
  (:use cache-dot-clj.cache))

(defn slow [a] (Thread/sleep a) a)
(def fast (cached slow (lru-cache-strategy 3)))

(defmacro how-long [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         msecs# (/ (double (- (. System (nanoTime)) start#))
                   1000000.0)]
     {:msecs msecs# :ret ret#}))

(deftest is-caching
  (let [delay 100]
    (invalidate-cache fast delay)
    (is (> (:msecs (how-long (fast delay))) delay)
        "First call hits function")
    (is (< (:msecs (how-long (fast delay))) delay)
        "Second call is cached")
    (is (< (:msecs (how-long (fast delay))) delay)
        "Third call is cached")))

(deftest is-returning
  (invalidate-cache fast 100)
  (is (= (:ret (how-long (fast 100))) 100)
      "First call return value")
  (is (= (:ret (how-long (fast 100))) 100)
      "Second call return value"))

(deftest invalidating
  (invalidate-cache fast 100)
  (invalidate-cache fast 200)
  (invalidate-cache fast 300)
  (is (> (:msecs (how-long (fast 100))) 100)
      "First call hits function")
  (is (> (:msecs (how-long (fast 200))) 200)
      "First call hits function")
  (is (> (:msecs (how-long (fast 300))) 300)
      "First call hits function")
  (invalidate-cache fast 100)
  (is (> (:msecs (how-long (fast 100))) 100)
      "Invalidated entry hits function")
  (is (< (:msecs (how-long (fast 200))) 200)
      "Second call is cached")
  (is (< (:msecs (how-long (fast 300))) 300)
      "Second call is cached")
  (is (< (:msecs (how-long (fast 100))) 100)
      "Third call is cached"))
  
(deftest overflow-lru
  (invalidate-cache fast 100)
  (invalidate-cache fast 200)
  (invalidate-cache fast 300)
  (invalidate-cache fast 400)
  (is (> (:msecs (how-long (fast 100))) 100)
      "First call hits function")
  (is (> (:msecs (how-long (fast 200))) 200)
      "First call hits function")
  (is (> (:msecs (how-long (fast 300))) 300)
      "First call hits function")
  (is (> (:msecs (how-long (fast 400))) 400)
      "First call hits function")
  (is (< (:msecs (how-long (fast 200))) 200)
      "Second call is cached")
  (is (< (:msecs (how-long (fast 300))) 300)
      "Second call is cached")
  (is (< (:msecs (how-long (fast 400))) 400)
      "Second call is cached")
  (is (> (:msecs (how-long (fast 100))) 100)
      "Function removed from cache")
  (is (< (:msecs (how-long (fast 300))) 300)
      "Third call is cached")
  (is (< (:msecs (how-long (fast 100))) 100)
      "Second call is cached"))
  
  

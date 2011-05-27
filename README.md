
clj-cache
=============

 A Clojure library that caches the results of impure functions. This library provides 3 internal caching strategies and can also cache externally or persistently using the java [ehcache](http://github.com/alienscience/clj-cache/blob/master/ehcache/README.md) package.

I have found this useful for caching the results of database calls and for holding HTML snippets.

This library is available at [clojars.org](http://clojars.org/uk.org.alienscience/clj-cache) for use with Leiningen, Cake or Maven.
/
     :dependencies [[uk.org.alienscience/clj-cache "0.0.3"]]

The internal caching functions consist of small modifications to the memoization functions described in these two excellent blog posts, [the rule of three](http://kotka.de/blog/2010/03/The_Rule_of_Three.html) and [memoize done right](http://kotka.de/blog/2010/03/memoize_done_right.html). I'd recommend these posts to Clojure programmers as they discuss flexible apis and concurrency in real world detail.


Example
-------

    (ns an-example
      (:use clj-cache.cache))

    (defn-cached get-user-from-db
      (lru-cache-strategy 1000)
      "Gets a user details from a database. Caches the last 1000
       users read in i.e support serving a 1000 concurrent users
       from memory."
      [username]
      ;; Slow database read goes here
    )

    ;; First read of the user is slow
    (get-user-from-db "fred")

    ;; Second is fast
    (get-user-from-db "fred")

    ;; When fred's details are changed invalidate the cache
    (invalidate-cache get-user-from-db "fred")

    ;; The next call will read from the db and cache the result again
    (get-user-from-db "fred")

Internal Algorithms
-------------------

    ;; Cache all calls with no limits
    naive-strategy

    ;; Least Recently Used
    (lru-cache-strategy cache-size)

    ;; Least Recently Used
    ;; (faster LRU removal, slower under multiple threads)
    (mutable-lru-cache-strategy cache-size)

    ;; Time to live
    (ttl-cache-strategy time-to-live-millisecs)

    ;; Least used
    (lu-cache-strategy cache-size)

I've found the Least Recently Used (LRU) algorithm to be the most robust for web applications.

External Algorithms
-------------------

Please see the READMEs in each subdirectory:

- An interface to [ehcache](http://github.com/alienscience/clj-cache/blob/master/ehcache/README.md). Ehcache provides persistent caches that survive application restarts and caches distributed over many machines.


Available Functions
-------------------

### cached

    ([f strategy])
     Returns a cached function that can be invalidated by calling
     invalidate-cache e.g
      (def fib (cached fib (lru-cache-stategy 5)))

### invalidate-cache

    ([cached-f & args])
     Invalidates the cache for the function call with the given arguments
     causing it to be re-evaluated e.g
      (invalidate-cache fib 30)  ;; A call to (fib 30) will not use the cache
      (invalidate-cache fib 29)  ;; A call to (fib 29) will not use the cache
      (fib 18)                   ;; A call to (fib 18) will use the cache

Available Macros
----------------

### defn-cached

    ([fn-name cache-strategy & defn-stuff])
    Macro
    Defines a cached function, like defn-memo from clojure.contrib.def
    e.g
     (defn-cached fib
        (lru-cache-strategy 10)
        [n]
        (if (<= n 1)
          n
          (+ (fib (dec n)) (fib (- n 2)))))

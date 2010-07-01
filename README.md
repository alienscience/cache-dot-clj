
Cache dot clj
=============

 A Clojure library that caches the results of impure functions.

I have found this useful for caching the results of database calls and for holding HTML snippets.

This library is available at [clojars.org](http://clojars.org/) for use with Leiningen/Maven
     :dependencies [[uk.org.alienscience/cache-dot-clj "0.0.1"]]

It consists of small modifications to the memoization functions described in this [excellent blog post](http://kotka.de/blog/2010/03/The_Rule_of_Three.html) that most Clojure programmers would find interesting.


Example
-------

    (ns an-example
      (:use cache-dot-clj.cache))

    (defn get-user-from-db
      "Gets a user details from a database."
      [username]
      ;; Slow database read goes here
    )

    ;; Cache the last 1000 users read in
    ;; i.e support serving a 1000 concurrent users from memory
    (def get-user-from-db 
      (cached get-user-from-db (lru-cache-strategy 1000)))

    ;; First read of the user is slow
    (get-user-from-db "fred")
 
    ;; Second is fast
    (get-user-from-db "fred")

    ;; When fred's details are changed invalidate the cache
    (invalidate-cache get-user-from-db "fred")

    ;; The next call will read from the db and cache the result again
    (get-user-from-db "fred")

Available Algorithms
--------------------

    ;; Cache all calls with no limits
    (cached fn-name)

    ;; Least Recently Used
    (cached fn-name (lru-cache-strategy cache-size))

    ;; Time to live
    (cached fn-name (ttl-cache-strategy time-to-live-millisecs))

    ;; Least used
    (cached fn-name (lu-cache-strategy cache-size))

I've found the Least Recently Used (LRU) algorithm to be the most robust for web applications.

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

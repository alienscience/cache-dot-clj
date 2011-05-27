(ns clj-cache.cache
  "Resettable memoize"
  (:require [clj-cache.datastructures :as ds]))

(declare naive-strategy)

(defn- external-memoize
  "Conventional memoize for use with external caching packages

  You may pass in an options map with the following keys:

    - :cache-key   – function applied to the args to generate the cache-key. Defaults to the identity function.
"
  [f f-name strategy & [options]]
  (let [{:keys [init lookup miss! invalidate!]} strategy
        cache (init f-name)
        cache-key (or (:cache-key options) identity)]
    {:memoized
     (fn [& args]
       (let [key (cache-key args)
             [in-cache? res] (lookup cache key)]
         (if in-cache?
           res
           (miss! cache key (apply f args)))))
     :invalidate
     (fn [args]
       (invalidate! cache (cache-key args)))
     }))

;; TODO Move some of doc up a level
(defn- internal-memoize
  "Returns a map containing:

    {:memoized fn     ;; Memoized version of given function
     :invalidate fn   ;; Invalidate arguments in the cache
    }

  The memoized version of the function keeps a cache of the mapping from
  arguments to results and, when calls with the same arguments are repeated
  often, has higher performance at the expense of higher memory use.

  The invalidation function takes a set of function arguments and causes
  the appropriate cache entry to re-evaluate the memoized function.
  Invalidation can be used to support the memoization of functions
  that can be effected by external events.

  Takes a cache strategy. The strategy is provided as a map
  containing the following keys. All keys are mandatory!

    - :init   – the initial value for the cache and strategy state
    - :cache  – access function to access the cache
    - :lookup – determines whether a value is in the cache or not
    - :hit    – a function called with the cache state and the argument
                list in case of a cache hit
    - :miss   – a function called with the cache state, the argument list
                and the computation result in case of a cache miss
    - :invalidate - a function called with the cache state, the argument
                    list and the computation result that is used to
                    invalidate the cache entry for the computation.

  You may pass in an options map with the following keys:

    - :cache-key   – function applied to the args to generate the cache-key. Defaults to the identity function.
  "
  [f _ strategy & [options]]
  (let [{:keys [init cache lookup hit miss invalidate]} strategy
        cache-key (or (:cache-key options) identity)
        cache-state (atom init)
        hit-or-miss (fn [state args]
                      (let [key (cache-key args)]
                        (if (lookup state key)
                          (hit state key)
                          (miss state key (delay (apply f args))))))
        mark-dirty (fn [state args]
                     (let [key (cache-key args)]
                       (if (lookup state key)
                         (invalidate state key (delay (apply f args)))
                         state)))]
    {:memoized
     (fn [& args]
       (let [cs (swap! cache-state hit-or-miss args)]
         (-> cs cache (get (cache-key args)) deref)))
     :invalidate
     (fn [args]
       (if (empty? args)
         (reset! cache-state init)
         (swap! cache-state mark-dirty args))
       nil)}))

(defmacro defn-cached
  "Defines a cached function, like defn-memo from clojure.contrib.def
   e.g
     (defn-cached fib
        (lru-cache-strategy 10)
        [n]
        (if (<= n 1)
          n
          (+ (fib (dec n)) (fib (- n 2)))))

   Following the strategy you can pass in an options map with the following keys:

    - :cache-key   – function applied to the args to generate the cache-key. Defaults to the identity function.
"
  [fn-name cache-strategy & options-and-defn-body]
  (let [[options defn-body] (if (map? (first options-and-defn-body))
                               [(first options-and-defn-body) (rest options-and-defn-body)]
                               [{} options-and-defn-body])]
    `(let [f-name# (str *ns* "." '~fn-name)]
       (defn ~fn-name ~@defn-body)
       (alter-var-root (var ~fn-name)
                       cached* f-name# ~cache-strategy ~options)
       (var ~fn-name))))

(def function-utils (atom {}))

(def memoizers* {:external-memoize external-memoize
                 :internal-memoize internal-memoize})

(defn cached*
  "Sets up a cache for the given function with the given name"
  [f f-name strategy & [options]]
  (let [memoizer (-> strategy :plugs-into memoizers*)
        internals (memoizer f f-name strategy options)
        cached-f (:memoized internals)
        utils (dissoc internals :memoized)]
    (if (and (= memoizer external-memoize)
             (= f-name :anon))
      (throw (Exception. (str (strategy :description)
                              " does not support anonymous functions"))))
    (when (and (not (:transient-cache options)) (not (empty? utils)))
      (swap! function-utils assoc cached-f utils))
    cached-f))

(defmacro cached
  "Returns a cached function that can be invalidated by calling
   invalidate-cache e.g
    (def fib (cached fib (lru-cache-stategy 5)))

   Following the strategy you can pass in an options map with the following keys:

    - :cache-key   – function applied to the args to generate the cache-key. Defaults to the identity function.
    - :trainsient-cache   – cache will not be held onto by cache-dot-utils.  invalidate-cache does not work with these caches.

   e.g

    (def fib (cached fib (lru-cache-stategy 5) {:cach-key str}))
"
  [f strategy & [options]]
  (if-not (symbol? f)
    `(cached* ~f :anon ~strategy ~options)
    `(let [f-name# (str *ns* "." '~f)]
       (cached* ~f f-name# ~strategy ~options))))

(defn invalidate-cache
  "Invalidates the cache for the function call with the given arguments
   causing it to be re-evaluated e.g
     (invalidate-cache fun 30) 
     (fun 30)                   ;; Does not use cache
     (fun 30)                   ;; Uses cache" 
  [cached-f & args]
  (if-let [inv-fn (:invalidate (@function-utils cached-f))]
    (inv-fn args)))


;;======== Stategies for for memoize ==========================================

(def #^{:doc "A naive strategy for testing external-memoize"}
  naive-external-strategy
  {:init (fn [_] (atom {}))
   :lookup (fn [m args]
             (let [v (get @m args ::not-found)]
               (if (= v ::not-found)
                 [false nil]
                 [true v])))
   :miss! (fn [m args res]
            (swap! m assoc args res)
            res)
   :invalidate! (fn [m args]
                  (swap! m dissoc args)
                  nil)
   :description "Naive external strategy"
   :plugs-into :external-memoize})

(def #^{:doc "The naive save-all cache strategy for memoize."}
  naive-strategy
  {:init   {}
   :cache  identity
   :lookup contains?
   :hit    (fn [state _] state)
   :miss   assoc
   :invalidate assoc
   :plugs-into :internal-memoize})


(defn lru-cache-strategy
  "Implements a LRU cache strategy, which drops the least recently used
   argument lists from the cache. If the given limit of items in the cache
   is reached, the longest unaccessed item is removed from the cache. In
   case there is a tie, the removal order is unspecified.
   This implementation uses an immutable hash map which removes LRU entries in
   linear time. If performance is a problem with large caches consider using
   the mutable-lru-cache-strategy."
  [limit]
  {:init {:lru (into {} (for [x (range (- limit) 0)] [x x]))
          :tick 0
          :cache {}}
   :cache :cache
   :lookup (fn [state k] (contains? (:cache state) k))
   :hit (fn [state args]
          (-> state
              (assoc-in [:lru args] (:tick state))
              (update-in [:tick] inc)))
   :miss (fn [state args result]
           (let [k (apply min-key (:lru state)
                          (keys (:lru state)))]
             (-> state
                 (update-in [:lru] dissoc k)
                 (update-in [:cache] dissoc k)
                 (assoc-in [:lru args] (:tick state))
                 (update-in [:tick] inc)
                 (assoc-in [:cache args] result))))
   :invalidate (fn [state args placeholder]
                 (if (contains? (:lru state) args)
                   (assoc-in state [:cache args] placeholder)))
   :plugs-into :internal-memoize})


(defn mutable-lru-cache-strategy
  "Implements a LRU cache strategy, which drops the least recently used
  argument lists from the cache. If the given limit of items in the cache
  is reached, the longest unaccessed item is removed from the cache. In
  case there is a tie, the removal order is unspecified.
  This implementation uses a mutable java collection which is removes LRU
  entries in constant time."
  [limit]
  {:init        (ds/linked-hash-map limit)
   :cache       identity
   :lookup      contains?
   :hit         (fn [cache _] cache)
   :miss        (fn [cache args result]
                  (.put cache args result)
                  cache)
   :invalidate (fn [cache args placeholder]
                 (.put cache args placeholder)
                 cache)
   :plugs-into :internal-memoize})


(defn ttl-cache-strategy
  "Implements a time-to-live cache strategy. Upon access to the cache
  all expired items will be removed. The time to live is defined by
  the given expiry time span. Items will only be removed on function
  call. No background activity is done."
  [ttl]
  (let [dissoc-dead (fn [state now]
                      (let [ks (map key (filter #(> (- now (val %)) ttl)
                                                (:ttl state)))
                            dissoc-ks #(apply dissoc % ks)]
                        (-> state
                          (update-in [:ttl]   dissoc-ks)
                          (update-in [:cache] dissoc-ks))))]
    {:init   {:ttl {} :cache {}}
     :cache  :cache
     :lookup (fn [state args]
               (when-let [t (get (:ttl state) args)]
                 (< (- (System/currentTimeMillis) t) ttl)))
     :hit    (fn [state args]
               (dissoc-dead state (System/currentTimeMillis)))
     :miss   (fn [state args result]
               (let [now (System/currentTimeMillis)]
                 (-> state
                   (dissoc-dead now)
                   (assoc-in  [:ttl args] now)
                   (assoc-in  [:cache args] result))))
     :invalidate (fn [state args placeholder]
                   (if (contains? (:ttl state) args)
                     (assoc-in state [:cache args] placeholder)))
     :plugs-into :internal-memoize}))

(defn lu-cache-strategy
  "Implements a least-used cache strategy. Upon access to the cache
  it will be tracked which items are requested. If the cache size reaches
  the given limit, items with the lowest usage count will be removed. In
  case of ties the removal order is unspecified."
  [limit]
  {:init   {:lu (into {} (for [x (range (- limit) 0)] [x x])) :cache {}}
   :cache  :cache
   :lookup (fn [state k] (contains? (:cache state) k))
   :hit    (fn [state args] (update-in state [:lu args] inc))
   :miss   (fn [state args result]
             (let [k (apply min-key (:lu state) (keys (:lu state)))]
               (-> state
                 (update-in [:lu]    dissoc k)
                 (update-in [:cache] dissoc k)
                 (assoc-in  [:lu args] 0)
                 (assoc-in  [:cache args] result))))
   :invalidate (fn [state args placeholder]
                 (if (contains? (:lu state) args)
                   (assoc-in state [:cache args] placeholder)))
   :plugs-into :internal-memoize})

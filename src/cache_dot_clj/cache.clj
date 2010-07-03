(ns cache-dot-clj.cache "Resettable memoize")

(declare naive-strategy)

(defn- memoize-with-invalidate
  "Returns a sequence containing a memoized version of a given 
  function and a function that invalidates the memoized function 
  in the cache.

    [ memoized-fn invalidation-fn]

  The memoized version of the function keeps a cache of the mapping from
  arguments to results and, when calls with the same arguments are repeated
  often, has higher performance at the expense of higher memory use.

  The invalidation function takes a set of function arguments and causes
  the appropriate cache entry to re-evaluate the memoized function. 
  Invalidation can be used to support the memoization of functions
  that can be effected by external events.

  Optionally takes a cache strategy. The strategy is provided as a map
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
 
  The default strategy is the naive save-all strategy."
  ([f] (memoize-with-invalidate f naive-strategy))
  ([f strategy]
   (let [{:keys [init cache lookup hit miss invalidate]} strategy
         cache-state (atom init)
         hit-or-miss (fn [state args]
                       (if (lookup state args)
                         (hit state args)
                         (miss state args (delay (apply f args)))))
         mark-dirty (fn [state args]
                      (if (lookup state args)
                        (invalidate state args (delay (apply f args)))
                        state))]
     [
      (fn [& args]
        (let [cs (swap! cache-state hit-or-miss args)]
          (-> cs cache (get args) deref)))
      (fn [args]
        (swap! cache-state mark-dirty args)
        nil)])))

(defmacro defn-cached
  "Defines a cached function, like defn-memo from clojure.contrib.def"
  [fn-name cache-strategy & defn-stuff]
  `(do
     (defn ~fn-name ~@defn-stuff)
     (alter-var-root (var ~fn-name)
                     cached ~cache-strategy)
     (var ~fn-name)))

(def invalidators* (atom {}))

(defn cached 
  "Returns a cached function that can be invalidated by calling
   invalidate-cache e.g
    (def fib (cached fib (lru-cache-stategy 5)))"
  [f strategy]
  (let [[cached-f invalidate] (memoize-with-invalidate f strategy)]
    (swap! invalidators* assoc cached-f invalidate)
    cached-f))

(defn invalidate-cache 
  "Invalidates the cache for the function call with the given arguments
   causing it to be re-evaluated e.g
     (invalidate-cache fib 30)  ;; A call to (fib 30) will not use the cache
     (invalidate-cache fib 29)  ;; A call to (fib 29) will not use the cache
     (fib 18)                   ;; A call to (fib 18) will use the cache" 
  [cached-f & args]
  (if-let [inv-fn (@invalidators* cached-f)]
    (inv-fn args)))


;;======== Stategies for for memoize ==========================================

(def #^{:doc "The naive save-all cache strategy for memoize."}
  naive-strategy
  {:init   {}
   :cache  identity
   :lookup contains?
   :hit    (fn [state _] state)
   :miss   assoc
   :invalidate assoc})

(defn lru-cache-strategy
  "Implements a LRU cache strategy, which drops the least recently used
  argument lists from the cache. If the given limit of items in the cache
  is reached, the longest unaccessed item is removed from the cache. In
  case there is a tie, the removal order is unspecified."
  [limit]
  {:init   {:lru   (into {} (for [x (range (- limit) 0)] [x x]))
            :tick  0
            :cache {}}
   :cache  :cache
   :lookup (fn [state k] (contains? (:cache state) k))
   :hit    (fn [state args]
             (-> state
               (assoc-in  [:lru args] (:tick state))
               (update-in [:tick] inc)))
   :miss   (fn [state args result]
             (let [k (apply min-key (:lru state) (keys (:lru state)))]
               (-> state
                 (update-in [:lru]   dissoc k)
                 (update-in [:cache] dissoc k)
                 (assoc-in  [:lru args] (:tick state))
                 (update-in [:tick]  inc)
                 (assoc-in  [:cache args] result))))
   :invalidate (fn [state args placeholder]
                 (if (contains? (:lru state) args)
                   (assoc-in state [:cache args] placeholder)))})
                     

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
                     (assoc-in state [:cache args] placeholder)))}))

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
                   (assoc-in state [:cache args] placeholder)))})

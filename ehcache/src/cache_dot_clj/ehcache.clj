
(ns cache-dot-clj.ehcache
  (:import [net.sf.ehcache CacheManager Cache Element])
  (:import [net.sf.ehcache.config CacheConfiguration]))

(defn default
  "Returns an ehcache Cache object with default configuration"
  [cache-name]
  (let [cache-manager (CacheManager/create)]
    (if-not (.cacheExists cache-manager cache-name)
      (.addCache cache-manager cache-name))
    (.getCache cache-manager cache-name)))


(defn create-cache
  "Returns an ehcache Cache object with the given name and config."
  [cache-name config]
  (let [cache-manager (CacheManager/create)]
    (if-not (.cacheExists cache-manager cache-name)
      (do
        (.setName config cache-name)
        (.addCache cache-manager config))
      (.getCache cache-manager cache-name))))

(defn add
  "Adds an item to the given cache and returns the value added"
  [cache k v]
  (.put cache (Element. k v))
  v)

(defn lookup
  "Looks up an item in the given cache. Returns a vector:
    [element-exists? value]"
  [cache k]
  (let [element (.get cache k)]
    (if-not (nil? element)
      [true (.getValue element)]
      [false nil])))

(defn invalidate
  [cache k]
  (.remove cache k))

(defn- make-strategy
  "Create a strategy map for use with cache-dot-clj.cache"
  [init-fn]
  {:init init-fn
   :lookup lookup
   :miss! add
   :invalidate! invalidate
   :description "Ehcache backend"
   :plugs-into :external-memoize})

(defn create-config
  "Creates a CacheConfiguration object"
  []
  (CacheConfiguration.))

(defn strategy
  "Returns a strategy for use with cache-dot-clj.cache using the
   default configuration or the given cache configuration"
  ([] (make-strategy default))
  ([config]
     (make-strategy
      (fn [f-name]
        (create-cache f-name config)))))

;;------ Utils -----------------------------------------------------------------

(defn cache-seq
  "Returns a sequence containing the names of the currently used caches"
  []
  (seq (-> (CacheManager/create) .getCacheNames)))


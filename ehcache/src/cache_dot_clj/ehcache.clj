
(ns cache-dot-clj.ehcache
  (:import [net.sf.ehcache CacheManager Cache Element])
  (:import [net.sf.ehcache.config CacheConfiguration])
  (:require [cache-dot-clj.bean :as bean-utils])
  (:require [clojure.contrib.string :as str])
  (:use clojure.contrib.prxml))

(defn- to-camel-case
  "Converts an xml string into camelCase"
  [x]
  (str/replace-by #"-(\w)"
                  #(.toUpperCase (second %))
                  x))

(defn- to-xml-str
  "Converts a prxml compatible datastructure into a string
   containing xml"
  [ds]
  (with-out-str (prxml ds)))

(defn- not-xml
  "Converts a prxml compatible datastructure into an xml input stream
   for use by ehcache as a config"
  [config]
  (-> config 
      to-xml-str
      to-camel-case
      (.getBytes "UTF-8") 
      java.io.ByteArrayInputStream.))

(defn new-manager
  "Creates a new cache manager"
  ([]        (new CacheManager))
  ([config]
     (cond
       (vector? config) (CacheManager. (not-xml config))
       :else            (CacheManager. config))))

(defmacro defn-with-manager
  "Defines a function that takes a cache manager as an optional first parameter"
  [fn-name doc args & body]
  `(defn ~fn-name
     ~doc
     ([~@args] (~fn-name (CacheManager/getInstance) ~@args))
     ([~'manager ~@args]
        ~@body)))

(defn-with-manager remove-cache
  "Removes the cache with the given name"
  [cache-name]
  (if (.cacheExists manager cache-name)
      (.removeCache manager cache-name)))

(defn-with-manager default
  "Returns an ehcache Cache object with default configuration"
  [cache-name]
  (.addCacheIfAbsent manager cache-name))

(defn- add-cache
  "Adds the cache with the given config and name to the cache-manager"
  [cache-manager config cache-name]
  (.setName config cache-name)
  (let [cache (Cache. config)]
    (.addCacheIfAbsent cache-manager cache)))

(defn create-config
  "Creates a CacheConfiguration object"
  []
  (CacheConfiguration.))

(defn-with-manager create-cache
  "Returns an ehcache Cache object with the given name and config."
  [cache-name config]
  (if (map? config)
    (let [config-obj (create-config)]
      (bean-utils/update-bean config-obj config)
      (add-cache manager config-obj cache-name))
    (add-cache manager config cache-name)))

(defn add
  "Adds an item to the given cache and returns the value added"
  [cache k v]
  (.put cache (Element. (str k) v))
  v)

(defn lookup
  "Looks up an item in the given cache. Returns a vector:
    [element-exists? value]"
  [cache k]
  (let [element (.get cache (str k))]
    (if-not (nil? element)
      [true (.getValue element)]
      [false nil])))

(defn invalidate
  [cache k]
  (.remove cache (str k)))

(defn- make-strategy
  "Create a strategy map for use with cache-dot-clj.cache"
  [init-fn]
  {:init init-fn
   :lookup lookup
   :miss! add
   :invalidate! invalidate
   :description "Ehcache backend"
   :plugs-into :external-memoize})

(defn strategy
  "Returns a strategy for use with cache-dot-clj.cache using the
   default configuration or the given cache configuration"
  ([]       (make-strategy default))
  ([config] (strategy (CacheManager/getInstance) config))
  ([manager config]
     (make-strategy
      (fn [f-name]
        (create-cache manager f-name config)))))

;;------ Utils -----------------------------------------------------------------

(defn-with-manager cache-seq
  "Returns a sequence containing the names of the currently used caches
   within a cache manager"
  []
  (seq (.getCacheNames manager)))

(defn-with-manager delete-caches
  "Deletes caches in a cache manager"
  []
  (.removalAll manager))

(defn-with-manager shutdown
  "Shuts down a cache manager"
  []
  (doseq [cache-name (cache-seq manager)
          :let [i (.getCache manager cache-name)]]
    (.flush i))
  (.shutdown manager))

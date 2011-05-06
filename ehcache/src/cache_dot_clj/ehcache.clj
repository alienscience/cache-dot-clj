(ns cache-dot-clj.ehcache
  (:import [net.sf.ehcache CacheManager Cache Element]
           net.sf.ehcache.config.CacheConfiguration
           net.sf.ehcache.management.ManagementService
           javax.management.MBeanServer
           java.lang.management.ManagementFactory
           java.io.Serializable)
  (:require [cache-dot-clj.bean :as bean-utils])
  (:require [clojure.contrib.string :as str])
  (:use clojure.contrib.prxml))

(defprotocol ToCamelCase
  (to-camel-case [e] "Converts the keys in PRXML to be camel-case"))

(extend-protocol ToCamelCase
  String
  (to-camel-case [x] (str/replace-by #"-(\w)"
                                     #(.toUpperCase (second %))
                                     x))
  clojure.lang.Keyword
  (to-camel-case [k] (to-camel-case (name k)))
  clojure.lang.PersistentVector
  (to-camel-case [v]
                 (reduce (fn [so-far e]
                           (conj so-far (to-camel-case e)))
                         []
                         v))
  clojure.lang.PersistentArrayMap
  (to-camel-case [m]
                 (reduce (fn [so-far [k v]]
                           (assoc so-far
                             (to-camel-case k)
                             v))
                         {}
                         m)))
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
      to-camel-case
      to-xml-str
      (.getBytes "UTF-8")
      java.io.ByteArrayInputStream.))

(defn new-manager
  "Creates a new cache manager. The config can be a filename string, URL
   object or an InputStream containing an XML configuration. To set the
   configuration without using an external XML file a clojure, prxml style,
   datastructure can be used."
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

;; By default the key (args of the fn) would be a clojure.lang.ArraySeq, and for some reason
;; seemily identical versions (i.e. = would be true) ehcache would have misses (only) after
;; persisted to disk.  By converting the ArraySeq's over then the keys match within ehcache.
(def cache-key vec)

(defn add
  "Adds an item to the given cache and returns the value added"
  [^Cache cache k ^Serializable v]
  (.put cache (Element. ^Serializable (cache-key k) v))
  v)

(defn lookup
  "Looks up an item in the given cache. Returns a vector:
    [element-exists? value]"
  [^Cache cache k]
  (if-let [^Element element (.get cache ^Serializable (cache-key k))]
    [true (.getValue element)]
    [false nil]))

(defn invalidate
  [^Cache cache k]
  (.remove cache ^Serializable (cache-key k)))

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
   default configuration or the given cache configuration.
   The config can be a object of class
     net.sf.ehcache.config.CacheConfiguration
   Or a clojure map containing keys that correspond to the setters
   of the Cache configuration. The keys are converted to camelCase internally
   , so for example:
       {:max-elements-in-memory 100} calls setMaxElementsInMemory(100)
   A CacheManager can also be passed in as the first argument, without this
   the singleton CacheManager is used (which should be fine for most uses)."
  ([]       (make-strategy default))
  ([manager-or-config]
     (if (instance? CacheManager manager-or-config)
       (make-strategy (partial default manager-or-config))
       (strategy (CacheManager/getInstance) manager-or-config)))
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


(defn-with-manager register-with-jmx
  "Registers a cache manager's mbean with configuration and statistic information."
  []
  (ManagementService/registerMBeans manager
                                    (ManagementFactory/getPlatformMBeanServer)
                                    true true true true)
  manager)

(defn-with-manager shutdown
  "Shuts down a cache manager"
  []
  (doseq [cache-name (cache-seq manager)
          :let [i (.getCache manager cache-name)]]
    (.flush i))
  (.shutdown manager))

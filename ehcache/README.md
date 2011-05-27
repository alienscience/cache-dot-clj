
# Ehcache support for clj-cache

This plugin allows clj-cache to be used with the java [Ehcache](http://ehcache.org/) library to provide distributed caching and persistence.

A PDF user guide for Ehcache is available [here](http://ehcache.org/documentation/EhcacheUserGuide-1.7.1.pdf).

Ehcache is flexible and powerful but comes with a large API and may require XML configuration. The `clj-cache.ehcache` namespace does its best to isolate casual users from the complexity without limiting access to the underlying java objects and features. However, feedback and feature requests are very welcome.


## Example using a default Ehcache configuration

    (ns an-example
      (:use clj-cache.cache)
      (:require [clj-cache.ehcache :as ehcache]))

    (defn-cached get-user-from-db
      (ehcache/strategy)
      [username]
      ;; Slow database read goes here
    )

## Example using persistence

    (defn-cached get-user-from-db
      (ehcache/strategy {:max-elements-in-memory 1000
                         :eternal true
                         :overflow-to-disk true
                         :disk-persistent true
                         :clear-on-flush true})
      [username]
      ;; Slow database read goes here
    )

## Dependencies

To use Ehcache and clj-cache pull in the following dependency using Leiningen, Cake or Maven:

     [clj-cache-ehcache "0.0.4"]

Ehcache uses slf4j to do logging and a slf4j plugin must be included as a dependency. To log to stderr you can use:

     [org.sljf4j/slf4j-simple "1.6.1"]

Or if logging is not required:

     [org.sljf4j/slf4j-nop "1.6.1"]


## Limitations

This package assumes all keys and values in the cache are `java.io.Serializable`. This covers most clojure datastructures but means that different versions of clojure (e.g 1.1 and 1.2) shouldn't share the same distributed cache.

Internally, clj-cache uses features found in clojure to limit the number of calls to slow functions on a cache miss. Ehcache can also do this using locking. Locking in Ehcache is relatively new, requires an additional package and is not well documented. Because of this clj-cache does not yet support locking with Ehcache. However, if there is interest, locking can be added at a later date.

# API

## strategy [] [config] [manager config]

Returns a strategy for use with clj-cache.cache using the
default configuration or the given cache configuration.
The config can be a object of class [net.sf.ehcache.config.CacheConfiguration](http://ehcache.org/apidocs/net/sf/ehcache/config/CacheConfiguration.html) or a clojure map containing keys that correspond to the setters
of the Cache configuration. The keys are converted to camelCase internally
, so for example:

       {:max-elements-in-memory 100} calls setMaxElementsInMemory(100)

A CacheManager can also be passed in as the first argument, without this the singleton CacheManager is used (which should be fine for most uses).

## new-manager [] [config]

Creates a new cache manager. The config can be a filename string, URL object or an InputStream containing an XML configuration. To set the configuration without using an external XML file, a clojure [prxml](http://richhickey.github.com/clojure-contrib/prxml-api.html#clojure.contrib.prxml/prxml) style datastructure can be used.

### example

    (new-manager
     [:ehcache
      [:disk-store
       {:path "java.io.tmpdir/mycaches"}]
      [:default-cache
       {:max-elements-in-memory 100
        :eternal false
        :overflow-to-disk true
        :disk-persistent false
        :memory-store-eviction-policy "LRU"}]])


## Access to Ehcache objects and features

- add [cache k v]

    Adds an item to the given cache and returns the value added.
- lookup [cache k]

    Looks up an item in the given cache. Returns a vector [element-exists? value].

- invalidate [cache k]

    Invalidates the cache entry with the given key.
- create-config []

    Creates a CacheConfiguration object.

The functions below can also be called with a CacheManager as the first argument. If a a CacheManager is not passed in then the singleton CacheManager is used.

- create-cache [cache-name config]

    Returns an ehcache Cache object with the given name and config.

- cache-seq []

    Returns a sequence containing the names of the currently used caches within a cache manager.

- remove-cache [cache-name]

    Removes the cache with the given name.

- shutdown []

    Shuts down a cache manager.


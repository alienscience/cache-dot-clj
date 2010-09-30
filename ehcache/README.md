
# Ehcache support for cache-dot-clj

Cache-dot-clj can be used with the java [Ehcache](http://ehcache.org/) package to provide distributed caching and persistence. 

Ehcache is an example of enterprise java culture, its flexible, comes with a large API and may require XML configuration. The `cache-dot-clj.ehcache` namespace does its best to isolate the innocent lisp hacker from all this without limiting access to the underlying java objects and features. 

## Example using a default Ehcache configuration

    (ns an-example
      (:use cache-dot-clj.cache)
      (:require [cache-dot-clj.ehcache :as ehcache]))

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

To use Ehcache and cache-dot-clj pull in the following dependency using Leiningen, Cake or Maven:
     [ehcache-dot-clj "0.0.3"]
     
Ehcache uses slf4j to do logging and a slf4j plugin must be included as a dependency. To log to stderr you can use:
     [org.sljf4j/slf4j-simple "1.5.11"]

## Limitations

This package assumes all keys and values in the cache are java.io.Serializable. This covers most clojure datastructures but means that different versions of clojure (e.g 1.1 and 1.2) shouldn't share the same distributed cache.

# API

## strategy [] [config] [manager config]

Returns a strategy for use with cache-dot-clj.cache using the
default configuration or the given cache configuration

## Creating a cache manager

- new-manager (TODO: example, mention persistence directory)

## Access to Ehcache objects and features

- add
- lookup
- invalidate
- create-config

The functions below can be called with a CacheManager as the first argument. If a a CacheManager is not passed in then the singleton CacheManager is used.

- create-cache
- cache-seq
- remove-cache
- shutdown

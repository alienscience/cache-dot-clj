# Masai support for cache-dot-clj

Masai is a common interface to several key-value stores. Right now, Masai supports both Redis and Tokyo Cabinet, and will support more in the future. This adds Masai support to cache-dot-clj, giving you the ability to cache to any of the databases that Masai supports.

## Example using Masai's redis backend

    (ns an-example
      (:use cache-dot-clj.cache)
      (:require [cache-dot-clj.masai :as masai]))

    (defn-cached get-user-from-db
      (masai/strategy)
      [username]
      ;; Slow database read goes here
    )

## Dependencies

To use Masai and cache-dot-clj pull in the following dependency using Leiningen, Cake or Maven:

     [uk.org.alienscience/masai-dot-clj "0.0.4-SNAPSHOT"]

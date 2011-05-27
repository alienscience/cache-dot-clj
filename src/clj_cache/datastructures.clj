
(ns clj-cache.datastructures
  "Datastructures for use with clj-cache"
  (:import [java.util LinkedHashMap Collections]))


(defn linked-hash-map
  "Creates a thread safe linked hash map set up to do LRU removal."
  [size]
  (Collections/synchronizedMap
   (proxy [LinkedHashMap] [(inc size) (float 0.75) true]
     (removeEldestEntry [eldest]
                        (> (.size this) size)))))


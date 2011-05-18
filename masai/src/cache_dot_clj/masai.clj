(ns cache-dot-clj.masai
  (:use [cereal format java]) 
  (require (masai [redis :as redis]
                  [tokyo :as tokyo]
                  [db :as db])))

(def form (make))

(defn add
  "Add an item to the given cache and return the value added."
  [^DB cache k v]
  (db/put! cache (str k) (encode form v))
  v)

(defn lookup
  "Looks up an item in the given cache. Returns a vector:
   [element-exists? value]"
  [^DB cache k]
  (let [record (db/get cache (str k))]
    [(-> record nil? not) (and record (decode form record))]))

(defn invalidate
  "Removes an item from the cache."
  [^DB cache k]
  (db/delete! cache (str k)))

(defn- make-strategy
  "Create a strategy map for use with cache-dot-clj.cache"
  [init-fn]
  {:init init-fn
   :lookup lookup
   :miss! add
   :invalidate! invalidate
   :description "Masai backend"
   :plugs-into :external-memoize})

(defn- prefix [f-name s] (str f-name "/" s))

(defn- key-format [f-name]
  (fn [^String s] (bytes (.getBytes (prefix f-name)))))

(defn- open [db]
  (db/open db)
  db)

(defn strategy
  "Returns a strategy for use with cache-dot-clj.cache. Given
   no arguments, uses Redis as the backend with default configuration.
   If passed an argument, that argument is expected to be a keyword
   naming the Masai backend to use. Possible keywords are :tokyo and
   :redis. If given two arguments, the second argument is expected to
   be a map of options to pass to whatever backend your using as options."
  ([] (make-strategy
       #(open (redis/make {:key-format (key-format %)}))))
  ([back opts]
     (case
      back
      :redis (make-strategy
              #(open
                (redis/make
                 (assoc opts :key-format (key-format %)))))
      :tokyo (make-strategy
              #(open
                (tokyo/make
                 (assoc opts :key-format (key-format %))))))))
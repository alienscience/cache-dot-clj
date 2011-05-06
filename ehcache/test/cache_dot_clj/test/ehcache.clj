(ns cache-dot-clj.test.ehcache
  "Ehcache tests"
  (:use clojure.test)
  (:use cache-dot-clj.cache)
  (:use [clojure.set :only [union]])
  (:use [clj-file-utils.core :only [rm-rf mkdir-p exists?]])
  (:require [cache-dot-clj.ehcache :as ehcache]
            [clojure.java.io :as io]
            [clojure.contrib.jmx :as jmx]))

;;--- Copy and paste of cache-dot-clj.test.cache (different src tree)

(defn slow [a] (Thread/sleep a) a)

;; Default ehcache setup has a ttl of 120 secs but unittests should
;; not be affected by this
(def fast-default (cached slow (ehcache/strategy)))

(defmacro how-long [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr
         msecs# (/ (double (- (. System (nanoTime)) start#))
                   1000000.0)]
     {:msecs msecs# :ret ret#}))

(defn expect [situation f check t should]
  (let [{:keys [msecs ret]} (how-long (f t))]
    (is (check msecs t) (str situation " (expected time:" t ", actual time: " msecs ") "  should))
    (is (= ret t) (str situation " returns correct value"))))

(defn is-caching [f t]
  (invalidate-cache f t)
  (expect "First call" f > t "hits function" )
  (expect "Second call" f < t "is cached")
  (expect "Third call" f < t "is cached"))

(deftest is-caching-ehcache (is-caching fast-default 100))

(defn invalidating [f t1 t2 t3]
  (invalidate-cache f t1)
  (invalidate-cache f t2)
  (invalidate-cache f t3)
  (expect "First call" f > t1 "hits function")
  (expect "First call" f > t2 "hits function")
  (expect "First call" f > t3 "hits function")
  (invalidate-cache f t1)
  (expect "Invalidated entry" f > t1 "hits function")
  (expect "Second call" f < t2 "is cached")
  (expect "Second call" f < t3 "is cached")
  (expect "Third call" f < t1 "is cached"))

(deftest invalidating-ehcache (invalidating fast-default 50 51 52))

(defn-cached cached-fn
  (ehcache/strategy)
  "A cached function definition"
  [t]
  (Thread/sleep t)
  t)

(deftest is-caching-def (is-caching cached-fn 100))


;; ------------- Persistence tests ----------------------------------------------

;; Directory to store peristent cache files
(def cache-directory* (str (io/file (System/getProperty "java.io.tmpdir")
                                    "ehcache")))

;; A CacheManager config to use for persistence tests
(def persistent-manager-config*
     [:ehcache
      [:disk-store
       {:path cache-directory*}]
      [:default-cache
       {:max-elements-in-memory 100
        :eternal false
        :overflow-to-disk false
        :disk-persistent false
        :memory-store-eviction-policy "LRU"}]])

;; A cache config to use for persistence tests
(def persistent-cache-config* {:max-elements-in-memory 100
                               :eternal true
                               :overflow-to-disk true
                               :disk-persistent true
                               :clear-on-flush true
                               :statistics true})

(deftest is-persistent
  ;; Start with a clean cache directory
  (if (exists? cache-directory*)
    (rm-rf cache-directory*))
  (mkdir-p cache-directory*)
  ;; Create the persistent cache
  (let [first-manager (ehcache/new-manager persistent-manager-config*)
        _ (ehcache/register-with-jmx first-manager)
        f (cached slow (ehcache/strategy first-manager
                                         persistent-cache-config*))]
    (is (not (empty? (jmx/mbean-names "net.sf.ehcache:*"))))
    (expect "First call" f > 100 "hits function")
    (expect "Second call" f > 101 "hits function")
    (expect "Third call" f > 102 "hits function")
    (expect "Second call" f < 100 "is cached")
    (expect "Second call" f < 101 "is cached")
    (expect "Second call" f < 102 "is cached")
    ;; Simulate end of VM
    (ehcache/shutdown first-manager))
  ;; Simulate start of new VM
  (let [second-manager (ehcache/new-manager persistent-manager-config*)
        f (cached slow (ehcache/strategy second-manager
                                         persistent-cache-config*))]
    (expect "First call" f < 100 "is cached")
    (expect "First call" f < 101 "is cached")
    (expect "First call" f < 102 "is cached")
    (ehcache/shutdown second-manager)))


(defrecord RecordThatHasNonUniqueToString [x]
  Object
  (toString [x] "Look at me, I'm always the same so I would be a horrible cache key."))

;; ------------- Misc tests ----------------------------------------------

;; This test ensures that we are internally using serialization in the key, as the docs advertise
(deftest serialization-is-used
  (let [cached-identity (cached identity (ehcache/strategy))]
    (is (not= (cached-identity (RecordThatHasNonUniqueToString. 55))
              (cached-identity (RecordThatHasNonUniqueToString. 42))))))

(deftest cache-names
  (let [cache-config {:max-elements-in-memory 100
                      :eternal false
                      :overflow-to-disk false
                      :disk-persistent false}
        manager (ehcache/new-manager [:ehcache
                                      [:default-cache
                                       cache-config]])
        _ (cached slow (ehcache/strategy manager cache-config))
        _ (cached identity (ehcache/strategy manager cache-config))
        expected #{"user.slow"  ;; *ns* is user here due to an artifact of how deftest runs I believe...
                   "user.identity"}]
    (is (= (set (ehcache/cache-seq manager))
           expected))))


;; ------------- Blocking tests ----------------------------------------------



(deftest blocking-cache-via-runtime-config
  (let [total (atom 0)
        side-effect (fn [x] (Thread/sleep x) (swap! total + x))
        cached-side-effect (cached side-effect (ehcache/strategy {:max-elements-in-memory 10 :block true}))]
    (future (cached-side-effect 100)) ;; Fire functions a the same time to ensure the second one blocks on first computation
    (cached-side-effect 100)
    (is (= 100 @total))))

(def manager-with-blocking-config*
  [:ehcache
   [:default-cache
    [:cache-decorator-factory {:class "net.sf.ehcache.constructs.blocking.BlockingCache"}] ;; Note the use of BlockingCache
    {:max-elements-in-memory 100
     :eternal false
     :overflow-to-disk false
     :disk-persistent false
     :memory-store-eviction-policy "LRU"}]])

(deftest blocking-cache-via-default-config
  (let [blocking-manager (ehcache/new-manager manager-with-blocking-config*)
        total (atom 0)
        side-effect (fn [x] (Thread/sleep x) (swap! total + x))
        cached-side-effect (cached side-effect (ehcache/strategy blocking-manager))]
    (future (cached-side-effect 100)) ;; Fire functions a the same time to ensure the second one blocks on first computation
    (cached-side-effect 100)
    (is (= 100 @total))
    (ehcache/shutdown blocking-manager)))



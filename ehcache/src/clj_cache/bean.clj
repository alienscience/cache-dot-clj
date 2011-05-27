
;;  Copyright (c) Justin Balthrop. All rights reserved.  The use and
;;  distribution terms for this software are covered by the Eclipse Public
;;  License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
;;  be found in the file epl-v10.html at the root of this distribution.  By
;;  using this software in any fashion, you are agreeing to be bound by the
;;  terms of this license.  You must not remove this notice, or any other
;;  from this software.
;;
;;  bean.clj
;;
;;  Modify bean attributes in clojure. Inspired by lancet.
;;
;;  code@justinbalthrop.com
;;  Created July 6, 2010

(ns
    #^{:author "Justin Balthrop"
       :doc "Modify bean attributes in clojure."}
  clj-cache.bean
  (:import [java.beans Introspector]))

(defn- property-key [property]
  (keyword (.. (re-matcher #"\B([A-Z])" (.getName property))
               (replaceAll "-$1")
               toLowerCase)))

(defn property-setters
  "Returns a map of keywords to property setter methods for a given class."
  [class]
  (reduce
   (fn [map property]
     (assoc map (property-key property) (.getWriteMethod property)))
   {} (.getPropertyDescriptors (Introspector/getBeanInfo class))))

(defmulti coerce (fn [bean-class type val] [type (class val)]))
(defmethod coerce :default [_ type val]
  (if (= String type)
    (str val)
    (try (cast type val)
         (catch ClassCastException e
           val))))

(defn update-bean
  "Update the given bean instance with attrs by calling the appropriate setter methods on it."
  [instance attrs]
  (let [bean-class (class instance)
        setters    (property-setters bean-class)]
    (doseq [[key val] attrs]
      (if-let [setter (setters key)]
        (when-not (nil? val)
          (let [type (first (.getParameterTypes setter))]
            (.invoke setter instance (into-array [(coerce bean-class type val)]))))
        (throw (Exception. (str "property not found for " key)))))
    instance))

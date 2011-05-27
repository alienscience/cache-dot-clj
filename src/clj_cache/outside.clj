
(ns clj-cache.outside
  "Functions to help with caches outside the current VM"
  (import [java.security MessageDigest])
  (import [sun.misc BASE64Encoder]))

(defn serialise
  "Serialises the given data structure. If the datastructure contains
   unprintable types they need to be handled by adding methods to
   the print-dup multimethod.
   The result can be deserialised using read-string"
  [d]
  (binding [*print-dup* true]
    (with-out-str (pr d))))

(defn digest-data
  "Returns the BASE64 encoded MD5 of the serialised form of the
   given data structure"
  [d]
  (let [md5 (MessageDigest/getInstance "MD5")
        d-bytes (.getBytes (serialise d) "UTF-8")
        md (.digest md5 d-bytes)
        b64 (new BASE64Encoder)]
    (.encode b64 md)))


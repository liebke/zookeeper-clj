(ns zookeeper.util
  (:import (org.apache.commons.codec.digest DigestUtils)
           (org.apache.commons.codec.binary Base64)))


(defn extract-id
  "Returns an integer id associated with a sequential node"
  ([child-path]
     (let [zk-seq-length 10]
       (Integer. (subs child-path
                       (- (count child-path) zk-seq-length)
                       (count child-path))))))

(defn index-sequential-nodes
  "Sorts a list of sequential child nodes."
  ([unsorted-nodes]
     (when (seq unsorted-nodes)
       (map (fn [node] [(extract-id node) node]) unsorted-nodes))))

(defn sort-sequential-nodes
  "Sorts a list of sequential child nodes."
  ([unsorted-nodes]
     (sort-sequential-nodes < unsorted-nodes))
  ([comp unsorted-nodes]
     (map second (sort-by first comp (index-sequential-nodes unsorted-nodes)))))

(defn filter-nodes-by-pattern
  ([pattern nodes]
     (filter #(re-find pattern %) nodes)))

(defn filter-nodes-by-prefix
  ([prefix nodes]
     (filter-nodes-by-pattern (re-pattern (str "^" prefix)) nodes)))

(defn hash-password
  " Returns a base64 encoded string of a SHA-1 digest of the given password string.

  Examples:

    (hash-password \"secret\")

"
  ([^String password]
     (Base64/encodeBase64String (DigestUtils/sha password))))
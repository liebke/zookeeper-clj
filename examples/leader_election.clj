(ns examples.leader-election
  (:require [zookeeper :as zk]
            [zookeeper.util :as util]))

(def root-znode "/election")

(def client (zk/connect "127.0.0.1:2181"))

(when-not (zk/exists client root-znode)
  (zk/create client root-znode :persistent? true))

(defn node-from-path [path]
  (.substring path (inc (count root-znode))))

(declare elect-leader)

(defn watch-predecessor [me pred leader {:keys [event-type path]}]
  (if (and (= event-type :NodeDeleted) (= (node-from-path path) leader))
    (println "I am the leader!")
    (if-not (zk/exists client (str root-znode "/" pred)
                       :watcher (partial watch-predecessor me pred leader))
      (elect-leader me))))

(defn predecessor [me coll]
  (ffirst (filter #(= (second %) me) (partition 2 1 coll))))

(defn elect-leader [me]
  (let [members (util/sort-sequential-nodes (zk/children client root-znode))
        leader (first members)]
    (print "I am" me)
    (if (= me leader)
      (println " and I am the leader!")
      (let [pred (predecessor me members)]
        (println " and my predecessor is:" pred)
        (if-not (zk/exists client (str root-znode "/" pred)
                           :watcher (partial watch-predecessor me pred leader))
          (elect-leader me))))))

(defn join-group []
  (let [me (node-from-path (zk/create client (str root-znode "/n-") :sequential? true))]
    (elect-leader me)))

(ns examples.group-membership
  (:require [zookeeper :as zk]))

(def group-name "/example-group")

(def client (zk/connect "127.0.0.1:2181"))

(when-not (zk/exists client group-name)
  (zk/create client group-name :persistent? true))

(defn group-watcher [x]
  (let [group (zk/children client group-name :watcher group-watcher)]
    (prn "Group members: " group)))

(defn join-group [name]
  (do (zk/create client (str group-name "/" name))
      (zk/children client group-name :watcher group-watcher)))


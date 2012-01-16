(ns zookeeper.server
  (:import (org.apache.zookeeper.server ZooKeeperServerMain
                                        ServerConfig)))

(defn server-config
  ([filename]
     (doto (ServerConfig.)
         (.parse filename))))

(defn start-server
  ([config-filename]
     (-> (ZooKeeperServerMain.)
         (.runFromConfig (server-config config-filename)))))

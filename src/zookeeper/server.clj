(ns zookeeper.server
  (:import (org.apache.zookeeper.server ZooKeeperServerMain
                                        ServerConfig)))

(defn server-config
  ([filename]
     (let [server-config (ServerConfig.)]
       (.parse server-config filename)
       server-config)))

(defn start-server
  ([config-filename]
     (-> (ZooKeeperServerMain.)
         (.runFromConfig (server-config config-filename)))))

(ns zookeeper.internal
  (:import (org.apache.zookeeper CreateMode
                                 Watcher
                                 ZooDefs$Perms
                                 ZooDefs$Ids
                                 ZooKeeper$States
                                 Watcher$Event$KeeperState
                                 Watcher$Event$EventType
                                 AsyncCallback$StringCallback
                                 AsyncCallback$VoidCallback
                                 AsyncCallback$StatCallback
                                 AsyncCallback$StatCallback
                                 AsyncCallback$Children2Callback
                                 AsyncCallback$DataCallback
                                 AsyncCallback$ACLCallback)))

(defmacro try*
  "Unwraps the RuntimeExceptions thrown by Clojure, and rethrows its cause. Only accepts a single expression."
  ([expression & catches]
     `(try
        (try
          ~expression
          (catch Throwable e# (throw (or (.getCause e#) e#))))
        ~@catches)))


(defn stat-to-map
  ([^org.apache.zookeeper.data.Stat stat]
     ;;(long czxid, long mzxid, long ctime, long mtime, int version, int cversion, int aversion, long ephemeralOwner, int dataLength, int numChildren, long pzxid)
     (when stat
       {:czxid (.getCzxid stat)
        :mzxid (.getMzxid stat)
        :ctime (.getCtime stat)
        :mtime (.getMtime stat)
        :version (.getVersion stat)
        :cversion (.getCversion stat)
        :aversion (.getAversion stat)
        :ephemeralOwner (.getEphemeralOwner stat)
        :dataLength (.getDataLength stat)
        :numChildren (.getNumChildren stat)
        :pzxid (.getPzxid stat)})))

(defn event-to-map
  ([^org.apache.zookeeper.WatchedEvent event]
     (when event
       {:event-type (keyword (.name (.getType event)))
        :keeper-state (keyword (.name (.getState event)))
        :path (.getPath event)})))

;; Watcher

(defn make-watcher
  ([handler]
     (reify Watcher
       (process [this event]
         (handler (event-to-map event))))))

;; Callbacks

(defn string-callback
  ([handler]
     (reify AsyncCallback$StringCallback
       (processResult [this return-code path context name]
         (handler {:return-code return-code
                   :path path
                   :context context
                   :name name})))))

(defn stat-callback
  ([handler]
     (reify AsyncCallback$StatCallback
       (processResult [this return-code path context stat]
         (handler {:return-code return-code
                   :path path
                   :context context
                   :stat (stat-to-map stat)})))))

(defn children-callback
  ([handler]
     (reify AsyncCallback$Children2Callback
       (processResult [this return-code path context children stat]
         (handler {:return-code return-code
                   :path path
                   :context context
                   :children (seq children)
                   :stat (stat-to-map stat)})))))

(defn void-callback
  ([handler]
     (reify AsyncCallback$VoidCallback
       (processResult [this return-code path context]
         (handler {:return-code return-code
                   :path path
                   :context context})))))

(defn data-callback
  ([handler]
     (reify AsyncCallback$DataCallback
       (processResult [this return-code path context data stat]
         (handler {:return-code return-code
                   :path path
                   :context context
                   :data data
                   :stat (stat-to-map stat)})))))

(defn acl-callback
  ([handler]
     (reify AsyncCallback$ACLCallback
       (processResult [this return-code path context acl stat]
         (handler {:return-code return-code
                   :path path
                   :context context
                   :acl (seq acl)
                   :stat (stat-to-map stat)})))))

(defn promise-callback
  ([prom callback-fn]
     (fn [{:keys [return-code path context name] :as result}]
       (deliver prom result)
       (when callback-fn
         (callback-fn result)))))

;; states

(def create-modes { ;; The znode will not be automatically deleted upon client's disconnect
                   {:persistent? true, :sequential? false} CreateMode/PERSISTENT
                   ;; The znode will be deleted upon the client's disconnect, and its name will be appended with a monotonically increasing number
                   {:persistent? false, :sequential? true} CreateMode/EPHEMERAL_SEQUENTIAL
                   ;; The znode will be deleted upon the client's disconnect
                   {:persistent? false, :sequential? false} CreateMode/EPHEMERAL
                   ;; The znode will not be automatically deleted upon client's disconnect, and its name will be appended with a monotonically increasing number
                   {:persistent? true, :sequential? true} CreateMode/PERSISTENT_SEQUENTIAL})



;; ACL

(def ^:dynamic perms {:write ZooDefs$Perms/WRITE
                      :read ZooDefs$Perms/READ
                      :delete ZooDefs$Perms/DELETE
                      :create ZooDefs$Perms/CREATE
                      :admin ZooDefs$Perms/ADMIN})

(defn perm-or
  "
  Examples:

    (use 'zookeeper)
    (perm-or *perms* :read :write :create)
"
  ([perms & perm-keys]
     (apply bit-or (vals (select-keys perms perm-keys)))))

(def acls {:open-acl-unsafe ZooDefs$Ids/OPEN_ACL_UNSAFE ;; This is a completely open ACL
          :anyone-id-unsafe ZooDefs$Ids/ANYONE_ID_UNSAFE ;; This Id represents anyone
          :auth-ids ZooDefs$Ids/AUTH_IDS ;; This Id is only usable to set ACLs
          :creator-all-acl ZooDefs$Ids/CREATOR_ALL_ACL ;; This ACL gives the creators authentication id's all permissions
          :read-all-acl ZooDefs$Ids/READ_ACL_UNSAFE ;; This ACL gives the world the ability to read
          })

(defn event-types
  ":NodeDeleted :NodeDataChanged :NodeCreated :NodeChildrenChanged :None"
  ([] (into #{} (map #(keyword (.name ^Watcher$Event$EventType %)) (Watcher$Event$EventType/values)))))

(defn keeper-states
  ":AuthFailed :Unknown :SyncConnected :Disconnected :Expired :NoSyncConnected"
  ([] (into #{} (map #(keyword (.name ^Watcher$Event$KeeperState %)) (Watcher$Event$KeeperState/values)))))

(defn client-states
  ":AUTH_FAILED :CLOSED :CONNECTED :ASSOCIATING :CONNECTING"
  ([] (into #{} (map #(keyword (.toString ^ZooKeeper$States %)) (ZooKeeper$States/values)))))



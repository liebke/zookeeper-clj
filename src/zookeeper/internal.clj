(ns zookeeper.internal
  (:import (org.apache.zookeeper CreateMode
                                 Watcher
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
          (catch Throwable e# (throw (.getCause e#))))
        ~@catches)))


(defn stat-to-map
  ([stat]
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
  ([event]
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




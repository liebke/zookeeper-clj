(ns zookeeper
  "
    Zookeeper-clj is a Clojure DSL for <a href=\"http://zookeeper.apache.org/\">Apache ZooKeeper</a>, which \"<i>is a centralized service for maintaining configuration information, naming, providing distributed synchronization, and providing group services.</i>\"

  Out of the box ZooKeeper provides name service, configuration, and group membership. From these core services, higher-level distributed concurrency abstractions can be built, including distributed locks, distributed queues, barriers, leader-election, and transaction services as described in <a href=\"http://zookeeper.apache.org/doc/trunk/recipes.html\">ZooKeeper Recipes and Solutions</a> and the paper <a href=\"http://www.usenix.org/event/atc10/tech/full_papers/Hunt.pdf\">\"ZooKeeper: Wait-free coordination for Internet-scale systems\"</a>.

  See examples:

  * http://developer.yahoo.com/blogs/hadoop/posts/2009/05/using_zookeeper_to_tame_system/
  * http://archive.cloudera.com/cdh/3/zookeeper/zookeeperProgrammers.pdf

  "
  (:import (org.apache.zookeeper ZooKeeper KeeperException KeeperException$BadVersionException)
           (org.apache.zookeeper.data Stat Id ACL)
           (java.util.concurrent CountDownLatch TimeUnit)
           (java.util Arrays))
  (:require [clojure.string :as s]
            [zookeeper.internal :as zi]
            [zookeeper.util :as util]))

;; connection functions

(defn close
  "Closes the connection to the ZooKeeper server."
  ([^ZooKeeper client] (.close client)))

(defn connect
  "Returns a ZooKeeper client."
  ([connection-string & {:keys [timeout-msec watcher]
                         :or {timeout-msec 5000}}]
   (let [latch (CountDownLatch. 1)
         session-watcher (zi/make-watcher (fn [event]
                                            (when (= (:keeper-state event) :SyncConnected)
                                              (.countDown latch))
                                            (when watcher (watcher event))))
         client (ZooKeeper. connection-string timeout-msec session-watcher)]
     (.await latch timeout-msec TimeUnit/MILLISECONDS)
     (if (= (.getCount latch) 0)
       client
       (do (close client)
           (throw (IllegalStateException. "Cannot connect to server")))))))

(defn register-watcher
  "Registers a default watcher function with this connection.
  Overrides the watcher specified during connection."
  ([^ZooKeeper client watcher]
   (.register client (zi/make-watcher watcher))))

(defn state
  "Returns the client's current state.
  One of:
  :ASSOCIATING :AUTH_FAILED :CLOSED :CONNECTED :CONNECTEDREADONLY :CONNECTING :NOT_CONNECTED"
  ([^ZooKeeper client]
   (keyword (.toString (.getState client)))))

;; node existence function

(defn exists
  "Returns the status of the given node, or nil if the node does not exist.

  If the watch is true and the call is successful (no exception is
  thrown), a watch will be left on the node with the given path. The
  watch will be triggered by a successful operation that creates/delete
  the node or sets the data on the node.

  If a watcher is provided the function will be called asynchronously
  with the provided watcher.

  If a callback is provided or `async?` is true, exists will be called
  asynchronously and return a promise.

  Examples:

    (use 'zookeeper)
    (def client (connect \"127.0.0.1:2181\"
                         :wacher #(println \"event received: \" %)))

    (defn callback [result]
      (println \"got callback result: \" result))

    (exists client \"/yadda\" :watch? true)
    (create client \"/yadda\")
    (exists client \"/yadda\")
    (def p0 (exists client \"/yadda\" :async? true))
    @p0
    (def p1 (exists client \"/yadda\" :callback callback))
    @p1
  "
  ([^ZooKeeper client ^String path & {:keys [watcher watch? async? callback context]
                                      :or {watch? false
                                           async? false
                                           context path}}]
   (when path
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
          (if watcher
            (.exists client path
                     (zi/make-watcher watcher)
                     (zi/stat-callback (zi/promise-callback prom callback))
                     ^Object context)
            (.exists client path
                     (boolean watch?)
                     (zi/stat-callback (zi/promise-callback prom callback))
                     ^Object context))
          (catch KeeperException e (throw e)))
         prom)
       (zi/try*
        (if watcher
          (zi/stat-to-map (.exists client path (zi/make-watcher watcher)))
          (zi/stat-to-map (.exists client path (boolean watch?))))
        (catch KeeperException e (throw e)))))))

;; node creation functions

(defn create
  " Creates a node, returning either the node's name, or a promise
    with a result map if either the :async? option is true or if a
    :callback function is provided. If the node already exists,
    create will return false.

  Options:

    :persistent? indicates if the node should be persistent
    :sequential? indicates if the node should be sequential
    :data data to associate with the node
    :acl access control, see the acls map
    :async? indicates that the create should occur asynchronously,
            a promise will be returned
    :callback indicates that the create should occur asynchronously
              and that this function should be called when it does,
              a promise will also be returned


  Example:

    (use 'zookeeper)
    (def client (connect \"127.0.0.1:2181\" :watcher #(println \"event received: \" %)))

    (defn callback [result]
      (println \"got callback result: \" result))

    ;; first delete the baz node if it exists
    (delete-all client \"/baz\")
    ;; now create a persistent parent node, /baz, and two child nodes
    (def p0 (create client \"/baz\" :callback callback :persistent? true))
    @p0
    (def p1 (create client \"/baz/1\" :callback callback))
    @p1
    (def p2 (create client \"/baz/2-\" :async? true :sequential? true))
    @p2
    (create client \"/baz/3\")

  "
  ([^ZooKeeper client path & {:keys [data acl persistent? sequential? context callback async?]
                              :or {persistent? false
                                   sequential? false
                                   acl (zi/acls :open-acl-unsafe)
                                   context path
                                   async? false}}]
   (if (or async? callback)
     (let [prom (promise)]
       (zi/try*
        (zi/create client path data acl
                   (zi/create-modes {:persistent? persistent?, :sequential? sequential?})
                   (zi/string-callback (zi/promise-callback prom callback))
                   context)
        (catch KeeperException e (throw e)))
       prom)
     (zi/try*
      (zi/create client path data acl
                 (zi/create-modes {:persistent? persistent?, :sequential? sequential?}))
      (catch org.apache.zookeeper.KeeperException$NodeExistsException _
        false)
      (catch KeeperException e (throw e))))))

(defn create-all
  "Create a node and all of its parents. The last node will be ephemeral,
   and its parents will be persistent.

  Options like :persistent? :sequential? and :acl will only be applied
  to the last child node.

  Examples:
  (delete-all client \"/foo\")
  (create-all client \"/foo/bar/baz\" :persistent? true)
  (create-all client \"/foo/bar/baz/n-\" :sequential? true)


  "
  ([^ZooKeeper client path & options]
   (when path
     (loop [result-path "" [dir & children] (rest (s/split path #"/"))]
       (if dir
         (let [node (str result-path "/" dir)]
           (if (exists client node)
             (recur node children)
             (recur (or (if (seq children)
                          (create client node :persistent? true)
                          (apply create client node options))
                        node)
                    children)))
         result-path)))))

;; children functions

(defn children
  "Returns a sequence of child node name for the given node, nil if it
  has no children or false if the node does not exist.

  Examples:

    (use 'zookeeper)
    (def client (connect \"127.0.0.1:2181\" :watcher #(println \"event received: \" %)))

    (defn callback [result]
      (println \"got callback result: \" result))

    (delete-all client \"/foo\")
    (create client \"/foo\" :persistent? true)
    (repeatedly 5 #(create client \"/foo/child-\" :sequential? true))

    (children client \"/foo\")
    (def p0 (children client \"/foo\" :async? true))
    @p0
    (def p1 (children client \"/foo\" :callback callback))
    @p1
    (def p2 (children client \"/foo\" :async? true :watch? true))
    @p2
    (def p3 (children client \"/foo\" :async? true :watcher #(println \"watched event: \" %)))
    @p3

  "
  ([^ZooKeeper client ^String path & {:keys [watcher ^Boolean watch? async? callback context]
                                      :or {watch? false
                                           async? false
                                           context path}}]
   (when path
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
          (if watcher
            (seq (.getChildren client path (zi/make-watcher watcher)))
            (seq (.getChildren client path watch?)))
          (catch KeeperException e (throw e)))
         prom)
       (zi/try*
        (if watcher
          (seq (.getChildren client path (zi/make-watcher watcher)))
          (seq (.getChildren client path watch?)))
        (catch org.apache.zookeeper.KeeperException$NoNodeException _ false)
        (catch KeeperException e (throw e)))))))

;; filtering childrend

(defn filter-children-by-pattern
  "Returns a sequence of child node names filtered by the given regex pattern."
  ([^ZooKeeper client dir pattern]
   (when-let [children (children client dir)]
     (util/filter-nodes-by-pattern pattern children))))

(defn filter-children-by-prefix
  "Returns a sequence of child node names that start with the given prefix."
  ([^ZooKeeper client dir prefix]
   (filter-children-by-pattern client dir (re-pattern (str "^" prefix)))))

(defn filter-children-by-suffix
  "Returns a sequence of child node names that end with the given suffix."
  ([^ZooKeeper client dir suffix]
   (filter-children-by-pattern client dir (re-pattern (str suffix "$")))))

;; node deletion functions

(defn delete
  "Delete the given node if it exists.

  Examples:

    (use 'zookeeper)
    (def client (connect \"127.0.0.1:2181\" :watch #(println \"event received: \" %)))

    (defn callback [result]
      (println \"got callback result: \" result))

    (create client \"/foo\" :persistent? true)
    (create client \"/bar\" :persistent? true)

    (delete client \"/foo\")
    (def p0 (delete client \"/bar\" :callback callback))
    @p0
  "
  ([^ZooKeeper client path & {:keys [version async? callback context]
                              :or {version -1
                                   async? false
                                   context path}}]
   (when path
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
          (.delete client path version (zi/void-callback (zi/promise-callback prom callback)) context)
          (catch KeeperException e (throw e)))
         prom)
       (zi/try*
        (do
          (.delete client path version)
          true)
        (catch org.apache.zookeeper.KeeperException$NoNodeException _ false)
        (catch KeeperException e (throw e)))))))

(defn delete-all
  "Delete a node and all of its children."
  ([^ZooKeeper client path & options]
   (when path
     (doseq [child (or (children client path) nil)]
       (apply delete-all client (str path "/" child) options))
     (apply delete client path options))))

(defn delete-children
  "Delete all of a node's children."
  ([^ZooKeeper client path & options]
   (when path
     (let [{:keys [sort?] :or {sort? false}} options
           children (or (children client path) nil)]
       (doseq [child (if sort? (util/sort-sequential-nodes children) children)]
         (apply delete-all client (str path "/" child) options))))))

;; data functions

(defn data
  "Returns a map with two fields, `:data` and `:stat`:

  - `:stat`: node status. Same as returned by [[zookeeper/exists]].
  - `:data`: byte array of data saved on node.

  Examples:

    (use 'zookeeper)
    (def client (connect \"127.0.0.1:2181\" :watcher #(println \"event received: \" %)))

    (defn callback [result]
      (println \"got callback result: \" result))

    (delete-all client \"/foo\")
    (create client \"/foo\" :persistent? true :data (.getBytes \"Hello World\" \"UTF-8\"))
    (def result (data client \"/foo\"))
    (String. (:data result))
    (:stat result)

    (def p0 (data client \"/foo\" :async? true))
    @p0
    (String. (:data @p0))

    (def p1 (data client \"/foo\" :watch? true :callback callback))
    @p1
    (String. (:data @p1))

    (create client \"/foobar\" :persistent? true :data (.getBytes (pr-str {:a 1, :b 2, :c 3} \"UTF-8\")))
    (read-string (String. (:data (data client \"/foobar\"))))

  "
  ([^ZooKeeper client ^String path & {:keys [watcher ^Boolean watch? async? callback context]
                                      :or {watch? false
                                           async? false
                                           context path}}]
   (let [stat (Stat.)]
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
          (if watcher
            (.getData client path (zi/make-watcher watcher) (zi/data-callback (zi/promise-callback prom callback)) context)
            (.getData client path watch? (zi/data-callback (zi/promise-callback prom callback)) context))
          (catch KeeperException e (throw e)))
         prom)
       {:data (zi/try*
               (if watcher
                 (.getData client path (zi/make-watcher watcher) stat)
                 (.getData client path watch? stat))
               (catch KeeperException e (throw e)))
        :stat (zi/stat-to-map stat)}))))

(defn set-data
  "Set the data for the node of the given path if such a node exists and
  the given version matches the version of the node.
  if the given version is -1, it matches any node's versions.
  Return the stat of the node.

  Examples:

    (use 'zookeeper)
    (def client (connect \"127.0.0.1:2181\" :watcher #(println \"event received: \" %)))

    (defn callback [result]
      (println \"got callback result: \" result))

    (delete-all client \"/foo\")
    (create client \"/foo\" :persistent? true)

    (set-data client \"/foo\" (.getBytes \"Hello World\" \"UTF-8\") 0)
    (String. (:data (data client \"/foo\")))


    (def p0 (set-data client \"/foo\" (.getBytes \"New Data\" \"UTF-8\") 0 :async? true))
    @p0
    (String. (:data (data client \"/foo\")))

    (def p1 (set-data client \"/foo\" (.getBytes \"Even Newer Data\" \"UTF-8\") 1 :callback callback))
    @p1
    (String. (:data (data client \"/foo\")))

  "
  ([^ZooKeeper client path data version & {:keys [async? callback context]
                                           :or {async? false
                                                context path}}]
   (if (or async? callback)
     (let [prom (promise)]
       (zi/try*
        (.setData client path data version
                  (zi/stat-callback (zi/promise-callback prom callback)) context)
        (catch KeeperException e (throw e)))
       prom)
     (zi/try*
      (zi/stat-to-map (.setData client path data version))
      (catch KeeperException e (throw e))))))

(defn compare-and-set-data
  "Sets the data field of the given node, only if the current data
   byte-array equals (Arrays/equal) the given expected-value."
  ([^ZooKeeper client node ^bytes expected-value new-value]
   (let [{:keys [^bytes data stat]} (data client node)
         version (:version stat)]
     (try
       (when (Arrays/equals data expected-value)
         (set-data client node new-value version))
       (catch KeeperException$BadVersionException _
         ;; try again if the data has been updated before we were able to
         (compare-and-set-data client node expected-value new-value))))))

(defn alter-data
  "Sets the data field of the given node, using the value returned
   from a call to (fun current-data). If there is no data yet,
   current-data will be nil. fun should be free of side effects, as
   the operation will be retried as long as the version is different
   from that on the server."
  ([^ZooKeeper client path fun] (alter-data client path fun nil 0))
  ([^ZooKeeper client path fun data version]
     (or
      (try
        (zk/set-data client path (fun data) version)
        (catch KeeperException$BadVersionException e
          nil)) ;; return 'nil' for 'or' here, as we cannot 'recur' from here
      (let [{:keys [data stat]} (zk/data client path)
           version (:version stat)]
        (recur client path fun data version)))))

;; ACL

(defn get-acl
  "Returns a sequence of ACLs associated with the node at the given path
  and its stat.

  Examples:

    (use 'zookeeper)
    (def client (connect \"127.0.0.1:2181\" :watcher #(println \"event received: \" %)))
    (add-auth-info client \"digest\" \"david:secret\")

    (defn callback [result]
      (println \"got callback result: \" result))

    (delete-all client \"/foo\")
    (create client \"/foo\" :acl [(acl \"auth\" \"\" :read :write :create :delete)])
    (get-acl client \"/foo\")

    (def p0 (get-acl client \"/foo\" :async? true))

    (def p1 (get-acl client \"/foo\" :callback callback))

  "
  ([^ZooKeeper client path & {:keys [async? callback context]
                              :or {async? false
                                   context path}}]
   (let [stat (Stat.)]
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
          (.getACL client path stat (zi/acl-callback (zi/promise-callback prom callback)) context)
          (catch KeeperException e (throw e)))
         prom)
       {:acl (zi/try*
              (seq (.getACL client path stat))
              (catch KeeperException e (throw e)))
        :stat (zi/stat-to-map stat)}))))

(defn add-auth-info
  "Add auth info to connection."
  ([^ZooKeeper client scheme ^String auth]
   (zi/try*
    (.addAuthInfo client scheme (if (string? auth) (.getBytes auth "UTF-8") auth))
    (catch KeeperException e (throw e)))))

(defn acl-id
  "Returns an ACL Id object with the given scheme and ID value."
  ([scheme id-value]
   (Id. scheme id-value)))

(defn acl
  "Creates an ACL object.

  Examples:

    (use 'zookeeper)
    (def client (connect \"127.0.0.1:2181\" :watcher #(println \"event received: \" %)))

    (def open-acl-unsafe (acl \"world\" \"anyone\" :read :create :delete :admin :write))
    (create client \"/mynode\" :acl [open-acl-unsafe])

    (def ip-acl (acl \"ip\" \"127.0.0.1\" :read :create :delete :admin :write))
    (create client \"/mynode2\" :acl [ip-acl])

    (add-auth-info client \"digest\" \"david:secret\")

    ;; works
    (def auth-acl (acl \"auth\" \"\" :read :create :delete :admin :write))
    (create client \"/mynode4\" :acl [auth-acl])
    (data client \"/mynode4\")

    ;; change auth-info
    (add-auth-info client \"digest\" \"edgar:secret\")
    (data client \"/mynode4\")

  "
  ([scheme id-value perm & more-perms]
   (ACL. (apply zi/perm-or zi/perms perm more-perms) (acl-id scheme id-value))))

(def default-perms [:read :write :create :delete])

(defn world-acl
  "Create an instance of an ACL using the world scheme."
  ([& perms]
   (apply acl "world" "anyone" (or perms default-perms))))

(defn ip-acl
  "Create an instance of an ACL using the IP scheme."
  ([ip-address & perms]
   (apply acl "ip" ip-address (or perms default-perms))))

(defn host-acl
  "Create an instance of an ACL using the host scheme."
  ([host-suffix & perms]
   (apply acl "host" host-suffix (or perms default-perms))))

(defn auth-acl
  "Create an instance of an ACL using the auth scheme."
  ([& perms]
   (apply acl "auth" "" (or perms default-perms))))

(defn digest-acl
  "Create an instance of an ACL using the digest scheme."
  ([username password & perms]
   (apply acl "digest" (str username ":" password) (or perms default-perms))))

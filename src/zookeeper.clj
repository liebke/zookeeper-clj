(ns zookeeper
  "
  Zookeeper-clj is a Clojure DSL for Apache ZooKeeper.
  The core functions of ZooKeeper are name service,
  configuration, and group membership, and this
  functionality is provided by this library.

  See examples:

  * http://developer.yahoo.com/blogs/hadoop/posts/2009/05/using_zookeeper_to_tame_system/
  * http://archive.cloudera.com/cdh/3/zookeeper/zookeeperProgrammers.pdf

"
  (:import (org.apache.zookeeper ZooKeeper KeeperException)
           (org.apache.zookeeper.data Stat Id ACL)
           (java.util.concurrent CountDownLatch))
  (:require [clojure.string :as s]
            [zookeeper.internal :as zi]
            [zookeeper.util :as util]
            [zookeeper.logger :as log]))


;; connection functions

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
       (.await latch)
       client)))

(defn close ([client] (.close client)))

(defn register-watcher
  "Registers a default watcher function with this connection."
  ([client watcher]
     (.register client (zi/make-watcher watcher))))

(defn state
  "Returns current state of client, including :CONNECTING, :ASSOCIATING, :CONNECTED, :CLOSED, or :AUTH_FAILED"
  ([client]
     (keyword (.toString (.getState client)))))

;; node existence function

(defn exists
  "
  Examples:

    (use 'zookeeper)
    (def client (connect \"127.0.0.1:2181\" :wacher #(println \"event received: \" %)))

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
  ([client path & {:keys [watcher watch? async? callback context]
                   :or {watch? false
                        async? false
                        context path}}]
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
          (.exists client path (if watcher (zi/make-watcher watcher) watch?)
                   (zi/stat-callback (zi/promise-callback prom callback)) context)
          (catch KeeperException e
            (do
              (log/debug (str "exists: KeeperException Thrown: code: " (.code e) ", exception: " e))
              (throw e))))
         prom)
       (zi/try*
        (zi/stat-to-map (.exists client path (if watcher (zi/make-watcher watcher) watch?)))
        (catch KeeperException e
          (do
            (log/debug (str "exists: KeeperException Thrown: code: " (.code e) ", exception: " e))
            (throw e)))))))

;; node creation functions

(defn create
  " Creates a node, returning either the node's name, or a promise with a result map if the done asynchronously. If an error occurs, create will return false.

  Options:

    :persistent? indicates if the node should be persistent
    :sequential? indicates if the node should be sequential
    :data data to associate with the node
    :acl access control, see the acls map
    :async? indicates that the create should occur asynchronously, a promise will be returned
    :callback indicates that the create should occur asynchronously and that this function should be called when it does, a promise will also be returned


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
  ([client path & {:keys [data acl persistent? sequential? context callback async?]
                   :or {persistent? false
                        sequential? false
                        acl (zi/acls :open-acl-unsafe)
                        context path
                        async? false}}]
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
           (.create client path data acl
                    (zi/create-modes {:persistent? persistent?, :sequential? sequential?})
                    (zi/string-callback (zi/promise-callback prom callback))
                    context)
           (catch KeeperException e
             (do
               (log/debug (str "create: KeeperException Thrown: code: " (.code e) ", exception: " e))
               (throw e))))
         prom)
       (zi/try*
         (.create client path data acl
                  (zi/create-modes {:persistent? persistent?, :sequential? sequential?}))
         (catch org.apache.zookeeper.KeeperException$NodeExistsException e
           (log/debug (str "Tried to create an existing node: " path))
           false)
         (catch KeeperException e
           (do
             (log/debug (str "create: KeeperException Thrown: code: " (.code e) ", exception: " e))
             (throw e)))))))

(defn create-all
  "Create a node and all of its parents. The last node will be ephemeral,
   and its parents will be persistent. Option, like :persistent? :sequential?,
   :acl, will only be applied to the last child node.

  Examples:
  (delete-all client \"/foo\")
  (create-all client \"/foo/bar/baz\" :persistent? true)
  (create-all client \"/foo/bar/baz/n-\" :sequential? true)


"
  ([client path & options]
     (loop [parent "" [child & children] (rest (s/split path #"/"))]
       (if child
         (let [node (str parent "/" child)]
           (if (exists client node)
             (recur node children)
             (recur (if (seq children)
                      (create client node :persistent? true)
                      (apply create client node options))
                    children)))
         parent))))

;; children functions

(defn children
  "
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
  ([client path & {:keys [watcher watch? async? callback context sort?]
                   :or {watch? false
                        async? false
                        context path}}]
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
           (seq (.getChildren client path
                              (if watcher (zi/make-watcher watcher) watch?)
                              (zi/children-callback (zi/promise-callback prom callback)) context))
           (catch KeeperException e
             (do
               (log/debug (str "children: KeeperException Thrown: code: " (.code e) ", exception: " e))
               (throw e))))
         prom)
       (zi/try*
        (seq (.getChildren client path (if watcher (zi/make-watcher watcher) watch?)))
        (catch org.apache.zookeeper.KeeperException$NoNodeException e
          (log/debug (str "Tried to list children of a non-existent node: " path))
          false)
        (catch KeeperException e
          (do
            (log/debug (str "children: KeeperException Thrown: code: " (.code e) ", exception: " e))
            (throw e)))))))

;; filtering childrend

(defn filter-children-by-pattern
  ([client dir pattern]
     (when-let [children (children client dir)]
       (filter #(re-find pattern %) children))))

(defn filter-children-by-prefix
  ([client dir prefix]
     (filter-children-by-pattern client dir (re-pattern (str "^" prefix)))))

;; node deletion functions

(defn delete
  "Deletes the given node, if it exists

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
  ([client path & {:keys [version async? callback context]
                   :or {version -1
                        async? false
                        context path}}]
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
           (.delete client path version (zi/void-callback (zi/promise-callback prom callback)) context)
           (catch KeeperException e
             (do
               (log/debug (str "delete: KeeperException Thrown: code: " (.code e) ", exception: " e))
               (throw e))))
         prom)
       (zi/try*
         (do
           (.delete client path version)
           true)
         (catch org.apache.zookeeper.KeeperException$NoNodeException e
           (log/debug (str "Tried to delete a non-existent node: " path))
           false)
         (catch KeeperException e
           (do
             (log/debug (str "delete: KeeperException Thrown: code: " (.code e) ", exception: " e))
             (throw e)))))))

(defn delete-all
  "Deletes a node and all of its children."
  ([client path & options]
     (doseq [child (or (children client path) nil)]
       (apply delete-all client (str path "/" child) options))
     (apply delete client path options)))

(defn delete-children
  "Deletes all of the node's children."
  ([client path & options]
     (let [{:keys [sort?] :or {sort? false}} options
           children (or (children client path) nil)]
       (doseq [child (if sort? (util/sort-sequential-nodes children) children)]
         (apply delete-all client (str path "/" child) options)))))

;; data functions

(defn data
  "Returns byte array of data from given node.

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
  ([client path & {:keys [watcher watch? async? callback context]
                   :or {watch? false
                        async? false
                        context path}}]
     (let [stat (Stat.)]
       (if (or async? callback)
        (let [prom (promise)]
          (zi/try*
           (.getData client path (if watcher (zi/make-watcher watcher) watch?)
                     (zi/data-callback (zi/promise-callback prom callback)) context)
           (catch KeeperException e
             (do
               (log/debug (str "data: KeeperException Thrown: code: " (.code e) ", exception: " e))
               (throw e))))
          prom)
        {:data (zi/try*
                (.getData client path (if watcher (zi/make-watcher watcher) watch?) stat)
                (catch KeeperException e
                  (do
                    (log/debug (str "data: KeeperException Thrown: code: " (.code e) ", exception: " e))
                    (throw e))))
         :stat (zi/stat-to-map stat)}))))

(defn set-data
  "

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
  ([client path data version & {:keys [async? callback context]
                                :or {async? false
                                     context path}}]
     (if (or async? callback)
       (let [prom (promise)]
         (zi/try*
           (.setData client path data version
                     (zi/stat-callback (zi/promise-callback prom callback)) context)
           (catch KeeperException e
             (do
               (log/debug (str "set-data: KeeperException Thrown: code: " (.code e) ", exception: " e))
               (throw e))))
         prom)
       (zi/try*
         (zi/stat-to-map (.setData client path data version))
         (catch KeeperException e
           (do
             (log/debug (str "set-data: KeeperException Thrown: code: " (.code e) ", exception: " e))
             (throw e)))))))

;; ACL

(defn get-acl
 "
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
  ([client path & {:keys [async? callback context]
                   :or {async? false
                        context path}}]
     (let [stat (Stat.)]
       (if (or async? callback)
         (let [prom (promise)]
           (zi/try*
             (.getACL client path stat (zi/acl-callback (zi/promise-callback prom callback)) context)
             (catch KeeperException e
               (do
                 (log/debug (str "get-acl: KeeperException Thrown: code: " (.code e) ", exception: " e))
                 (throw e))))
         prom)
         {:acl (zi/try*
                (seq (.getACL client path stat))
                (catch KeeperException e
                  (do
                    (log/debug (str "get-acl: KeeperException Thrown: code: " (.code e) ", exception: " e))
                    (throw e))))
          :stat (zi/stat-to-map stat)}))))

(defn add-auth-info
  "Add auth info to connection."
  ([client scheme auth]
     (zi/try*
      (.addAuthInfo client scheme (if (string? auth) (.getBytes auth "UTF-8") auth))
      (catch KeeperException e
        (do
          (log/debug (str "add-auth-info: KeeperException Thrown: code: " (.code e) ", exception: " e))
          (throw e))))))

(defn acl-id
  ([scheme id-value]
     (Id. scheme id-value)))

(defn acl
  "
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
  ([& perms]
     (apply acl "world" "anyone" (or perms default-perms))))

(defn ip-acl
  ([ip-address & perms]
     (apply acl "ip" ip-address (or perms default-perms))))

(defn host-acl
  ([host-suffix & perms]
     (apply acl "host" host-suffix (or perms default-perms))))

(defn auth-acl
  ([& perms]
     (apply acl "auth" "" (or perms default-perms))))

(defn digest-acl
  ([username password & perms]
     (apply acl "digest" (str username ":" password) (or perms default-perms))))


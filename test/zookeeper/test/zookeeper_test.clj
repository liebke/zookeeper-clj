(ns zookeeper.test.zookeeper-test
  (:use [zookeeper]
        [clojure.test])
  (:import [java.util UUID]))

;; these tests require a local instance of ZooKeeper to be running on port 2181.

(def connect-string "127.0.0.1:2181")

(deftest dsl-test
  (let [parent-node (str "/test-" (UUID/randomUUID))
        child-node-prefix "child-"
        child0 (str child-node-prefix "0000000000")
        child1 (str child-node-prefix "0000000001")
        prom0 (promise)
        ref0 (ref [])
        make-watcher (fn [prom] (fn [event] (deliver prom event)))
        make-connection-watcher (fn [r] (fn [event] (dosync (alter r #(conj % event)))))
        client (connect connect-string :watcher (make-connection-watcher ref0))
        auth-client (doto (connect connect-string) (add-auth-info "digest" "david:secret"))
        data-string "test data"]

    ;; creation tests
    (is (= nil (exists client parent-node)))
    (is (= parent-node (create client parent-node :persistent? true)))
    (is (= :SyncConnected (:keeper-state (first @ref0)))) ;; the first event from the client will block

    ;; children tests
    (is (= 0 (:numChildren (exists client parent-node :watch? true)))) ;; will watch for delete of parent or children or data
    (is (nil? (children client parent-node :watch? true))) ;; will watch for change event in children
    (is (= (str parent-node "/" child0) (create client (str parent-node "/" child-node-prefix)
                                                :sequential? true)))
    (is (< 0 (:ephemeralOwner (exists client (str parent-node "/" child0)
                                      :watcher (make-watcher prom0))))) ;; add custom watcher
    (is (= 1 (:numChildren (exists client parent-node))))
    (is (= [child0] (children client parent-node :watch? true))) ;; will watch for change event in children
    (is (= (str parent-node "/" child1) (create client (str parent-node "/" child-node-prefix) :sequential? true)))
    (is (< 0 (:ephemeralOwner (exists client (str parent-node "/" child1)))))
    (is (= 2 (:numChildren (exists client parent-node))))
    (is (= #{child0 child1} (into #{} (children client parent-node))))

    ;; data tests
    (is (= 1 (:version (set-data client parent-node (.getBytes data-string) 0))))
    (is (= data-string (String. (:data (data client parent-node)))))
    (is (nil? (compare-and-set-data client parent-node (.getBytes "EXPECTED") (.getBytes "NEW VALUE"))))
    (is (= data-string (String. (:data (data client parent-node)))))
    (is (= 2 (:version (compare-and-set-data client parent-node (.getBytes data-string) (.getBytes "NEW VALUE")))))
    (is (= "NEW VALUE" (String. (:data (data client parent-node)))))

    ;; delete tests
    (is (true? (delete client (str parent-node "/" child0))))
    (is (= :NodeDeleted (:event-type @prom0))) ;; custom watcher event
    (is (= 1 (:numChildren (exists client parent-node :watch? true))))
    (is (true? (delete-all client (str parent-node))))
    (is (nil? (exists client parent-node)))

    ;; check the rest of the watcher events
    (is (= :NodeChildrenChanged (:event-type (nth @ref0 1))))
    (is (= :NodeChildrenChanged (:event-type (nth @ref0 2))))
    (is (= :NodeDataChanged (:event-type (nth @ref0 3))))
    (is (= :NodeDeleted (:event-type (nth @ref0 4))))

    ;; acl tests

    ;; create node that can only be accessed by its creator
    (is (= parent-node (create auth-client parent-node
                               :persistent? true
                               :acl [(acl "auth" "" :read :create :delete :write)])))
    ;; test authorized client
    (is (nil? (:data (data auth-client parent-node))))
    (is (nil? (children auth-client parent-node)))
    (is (.startsWith (create auth-client (str parent-node "/" child-node-prefix) :sequential? true)
                     (str parent-node "/" child-node-prefix)))
    ;; test unauthorized client
    (is (map? (exists client parent-node))) ;; don't need auth to check existence
    (is (thrown? org.apache.zookeeper.KeeperException$NoAuthException
                 (data client parent-node)))
    (is (thrown? org.apache.zookeeper.KeeperException$NoAuthException
                 (children client parent-node)))
    (is (thrown? org.apache.zookeeper.KeeperException$NoAuthException
                 (create client (str parent-node "/" child-node-prefix) :sequential? true)))

    ;; close the client
    (close client)))
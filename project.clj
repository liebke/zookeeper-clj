(defproject zookeeper-clj "0.9.3"
  :description "A Clojure DSL for Apache ZooKeeper"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.apache.zookeeper/zookeeper "3.4.6"]
                 [commons-codec "1.7"]
                 [org.apache.curator/curator-test "2.8.0" :scope "test"]])

(defproject zookeeper-clj "0.9.3"
  :description "A Clojure DSL for Apache ZooKeeper"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.apache.zookeeper/zookeeper "3.4.0" :exclusions [org.slf4j/slf4j-log4j12]]
                 [log4j/log4j "1.2.17"]
                 [commons-codec "1.7"]])

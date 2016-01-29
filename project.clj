(defproject zookeeper-clj "0.9.4"
  :description "A Clojure DSL for Apache ZooKeeper"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.apache.zookeeper/zookeeper "3.4.6"]
                 [commons-codec "1.7"]
                 [org.apache.curator/curator-test "2.8.0" :scope "test"]]
  :pedantic? :abort
  :repositories {"clojars" {:sign-releases false :url "https://clojars.org/repo/"}}
  :plugins [[lein-ancient "0.6.8"]
            [lein-cljfmt "0.3.0"]])

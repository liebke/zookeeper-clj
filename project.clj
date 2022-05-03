(defproject zookeeper-clj "0.10.0"
  :description "A Clojure DSL for Apache ZooKeeper"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.apache.zookeeper/zookeeper "3.8.0"]
                 [commons-codec "1.15"]
                 [org.apache.curator/curator-test "5.2.1" :scope "test"]]
  :repositories {"clojars" {:sign-releases false :url "https://clojars.org/repo/"}}
  :global-vars {*warn-on-reflection* true}
  :plugins [[lein-ancient "0.6.15"]
            [lein-cljfmt "0.6.8"]])

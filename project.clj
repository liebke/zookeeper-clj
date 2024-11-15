(defproject zookeeper-clj "0.13.0"
  :description "A Clojure DSL for Apache ZooKeeper"
  :dependencies [[org.clojure/clojure "1.11.2"]
                 [org.apache.zookeeper/zookeeper "3.9.3"]
                 [commons-codec "1.17.1"]
                 [org.apache.curator/curator-test "5.7.1" :scope "test"]]
  :deploy-repositories {"clojars" {:sign-releases false :url "https://repo.clojars.org"}}
  :global-vars {*warn-on-reflection* true}
  :url "https://github.com/liebke/zookeeper-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-ancient "0.7.0"]
            [lein-cljfmt "0.9.2"]])

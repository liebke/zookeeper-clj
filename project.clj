(defproject zookeeper-clj "0.11.0"
  :description "A Clojure DSL for Apache ZooKeeper"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.apache.zookeeper/zookeeper "3.9.1"]
                 [commons-codec "1.16.0"]
                 [org.apache.curator/curator-test "5.6.0" :scope "test"]]
  :deploy-repositories {"clojars" {:sign-releases false :url "https://repo.clojars.org"}}
  :global-vars {*warn-on-reflection* true}
  :url "https://github.com/liebke/zookeeper-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-ancient "0.7.0"]
            [lein-cljfmt "0.9.2"]])

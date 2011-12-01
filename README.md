# zookeeper-clj

Zookeeper-clj is a Clojure DSL for <a href="http://zookeeper.apache.org/">Apache ZooKeeper</a>, which "<i>is a centralized service for maintaining configuration information, naming, providing distributed synchronization, and providing group services.</i>"

Out of the box ZooKeeper provides name service, configuration, and group membership. From these core services, higher-level distributed concurrency abstractions can be built, including distributed locks, distributed queues, barriers, leader-election, and transaction services as described in <a href="http://zookeeper.apache.org/doc/trunk/recipes.html">ZooKeeper Recipes and Solutions</a> and the paper <a href="http://www.usenix.org/event/atc10/tech/full_papers/Hunt.pdf">"ZooKeeper: Wait-free coordination for Internet-scale systems"</a>. 

Building these distributed concurrency abstractions is the goal of the Java-based <a href="https://github.com/openUtility/menagerie">Menagerie</a> library and the, **soon to be released**, Clojure-based **Avout** library. Avout, in particular, provides distributed versions of Clojure's <a href="http://clojure.org/atoms">Atom</a> and <a href="http://clojure.org/refs">Ref</a> concurrency primitives, as well as distributed implementations of <a href="http://download.oracle.com/javase/1,5,0/docs/api/java/util/concurrent/locks/Lock.html">*java.util.concurrent.lock.Lock*</a> and <a href="http://download.oracle.com/javase/1,5,0/docs/api/java/util/concurrent/locks/ReadWriteLock.html">*java.util.concurrent.lock.ReadWriteLock*</a>.

## Table of Contents

* <a href="#getting-started">Getting Started</a>
  * <a href="#connect">connect function</a>
  * <a href="#watchers">watchers</a>
  * <a href="#create">create function</a>
  * <a href="#async">asynchronous calls</a>
  * <a href="#exists">exists function</a>
  * <a href="#children">children function</a>
  * <a href="#seq">sequential nodes</a>
  * <a href="#data">data functions</a>
  * <a href="#serialization">data serialization</a>
  * <a href="#delete">delete functions</a>
  * <a href="#acl">acl function</a>
* <a href="#group-membership">Group Membership Example</a>
* <a href="#leader-election">Leader Election Example</a>
* <a href="#barrier">Barrier Example</a>
* <a href="#running-zookeeper">Running ZooKeeper</a>
* <a href="#testing">Testing</a>
* <a href="#contributing">Contributing</a>
* <a href="#ref">References</a>


<a name="getting-started"></a>
## Getting Started

To run these examples, first start a local instance of ZooKeeper on port 2181, see <a href="#running-zookeeper">instructions below</a>, and include zookeeper-clj as a dependency by adding the following to your Leiningen project.clj file:

```clojure
    [zookeeper-clj "0.9.1"]
```

<a name="connect"></a>
### connect function

First require the zookeeper namespace and create a client with the connect function.

```clojure
    (require '[zookeeper :as zk])
    (def client (zk/connect "127.0.0.1:2181"))
```
    
The connection string is the name, or IP address, and port of the ZooKeeper server. Several host:port pairs can be included as a comma seperated list. The port can be left off if it is 2181.

The connection to the ZooKeeper server can be closed with the close function.

```clojure
    (zk/close client)
```

<a name="watchers"></a>
### watchers

A watcher function that takes a single event map argument can be passed to connect, which will be invoked as a result of changes of keeper-state, or as a result of other events. 

```clojure
    (def client (zk/connect "127.0.0.1" :watcher (fn [event] (println event))))
```
 
if the :watch? flag is set to true when using the exists, children, or data functions, the default watcher function will be triggered under the following circumstances.

* **exists**: the watch will be triggered by a successful operation that creates/deletes the node or sets the data on the node.
* **children**: the watch will be triggered by a successful operation that deletes the node of the given path or creates/deletes a child under the node.
* **data**: the watch will be triggered by a successful operation that sets data on the node, or deletes the node.

The default watcher function can be overriden with a custom function by passing it as the :watcher argument to the exists, children, or data functions.

The argument to the watcher function is a map with three keys: :event-type, :keeper-state, and :path.

* **event-type**: :NodeDeleted, :NodeDataChanged, :NodeCreated, :NodeChildrenChanged, :None
* **keeper-state**: :AuthFailed, :Unknown, :SyncConnected, :Disconnected, :Expired, :NoSyncConnected
* **path**: the path to the node in question, may be nil

NOTE: Watches are one time triggers; if you get a watch event and you want to get notified of future changes, you must set another watch.

<a name="create"></a>
### create function

Next, create a node called "/parent-node"

```clojure
    (zk/create client "/parent-node" :persistent? true)
    ;; => "/parent-node"
```
 
Setting the :persistent? flag to true creates a persistent node, meaning one that will persist even after the client that created it is no longer connected. By default, nodes are ephemeral (i.e. :persistent? false) and will be deleted if the client that created them is disconnected (this is key to how ZooKeeper is used to build robust distributed systems).

A node must be persistent if you want it to have child nodes.

<a name="async"></a>
### asynchronous calls

Most of the zookeeper functions can be called asynchronously by setting the :async? option to true, or by providing an explicit callback function with the :callback option. When invoked asynchronously, each function will return a promise that will eventually contain the result of the call (a map with the following keys: :return-code, :path, :context, :name).

```clojure
    (def result-promise (zk/create client "/parent-node" :persistent? true :async? true))
```

Dereferencing the promise will block until a result is returned.

```clojure
    @result-promise
```

If a :callback function is passed, the promise will be returned with the result map and the callback will be invoked with the same map.

```clojure
    (def result-promise (zk/create client "/parent-node" :persistent? true :callback (fn [result] (println result))))
```

<a name="exists"></a>
### exists function

We can check the existence of the newly created node with the exists function.

```clojure
    (zk/exists client "/parent-node")
```

The exists function returns nil if the node does not exist, and returns a map with the following keys if it does: :numChildren, :ephemeralOwner, :cversion, :mzxid, :czxid, :dataLength, :ctime, :version, :aversion, :mtime, :pzxid. See the ZooKeeper documentation for description of each field.

The exists function accepts the :watch?, :watcher, :async?, and :callback options. The watch functions will be triggered by a successful operation that creates/deletes the node or sets the data on the node.

<a name="children"></a>
### children function

Next, create a child node for "/parent-node"

```clojure
    (zk/create client "/parent-node/child-node")
    ;; => "/parent-node/child-node"
```

Since the :persistent? flag wasn't set to true, this node will be ephemeral, meaning it will be deleted if the client that created it is disconnected.

A list of a node's children can be retrieved with the children function.

```clojure
    (zk/children client "/parent-node")
    ;; => ("child-node")
```
 
If the node has no children, nil will be returned, and if the node doesn't exist, false will be returned. 

The children function accepts the :watch?, :watcher, :async?, and :callback options. The watch function will be triggered by a successful operation that deletes the node of the given path or creates/delete a child under the node.

<a name="seq"></a>
### sequential nodes

If the :sequential? option is set to true when a node is created, a ten digit sequential ID is appended to the name of the node (it's idiomatic to include a dash as the last character of a sequential node's name).

```clojure
    (zk/create-all client "/parent/child-" :sequential? true)
    ;; => "/parent/child-0000000000"
```

The create-all function creates the parent nodes if they don't already exist, here we used it to create the "/parent" node.

The sequence ID increases monotonically for a given parent directory.

```clojure
    (zk/create client "/parent/child-" :sequential? true)
    ;; => "/parent/child-0000000001"

    (zk/create client "/parent/child-" :sequential? true)
    ;; => "/parent/child-0000000002"
```

The zookeeper.util namespace contains functions for extracting IDs from sequential nodes and sorting them.

```clojure
    (require '[zookeeper.util :as util])
    (util/extract-id (first (zk/children client "/parent")))
    ;; => 2
```

The order of the child nodes return from children is arbitrary, but the nodes can be sorted with the sort-sequential-nodes function.

```clojure
    (util/sort-sequential-nodes (zk/children client "/parent"))
    ;; => ("child-0000000000" "child-0000000001" "child-0000000002")
```

<a name="data"></a>
### data functions

Each node has a data field that can hold a byte array, which is limited to 1M is size. 

The set-data function is used to insert data. The set-data function takes a version number, which needs to match the current data version. The current version is a field in the map returned by the exists function.

```clojure
    (def version (:version (zk/exists client "/parent")))

    (zk/set-data client "/parent" (.getBytes "hello world" "UTF-8") version)
```

The data function is used to retrieve the data stored in a node.
    
```clojure
    (zk/data client "/parent")
    ;; => {:data ..., :stat {...}}
```

The data function returns a map with two fields, :data and :stat. The :stat value is the same map returned by the exists function. The :data value is a byte array.

```clojure
    (String. (:data (zk/data client "/parent")) "UTF-8")
    ;; => "hello world"
```

The data function accepts the :watch?, :watcher, :async?, and :callback options. The watch function will be triggered by a successful operation that sets data on the node, or deletes the node.

<a name="serialization"></a>
### data serialization

The zookeeper.data namespace contains functions for serializing different primitive types to and from byte arrays.

```clojure
    (require '[zookeeper.data :as data])
    (def version (:version (zk/exists client "/parent")))
    (zk/set-data client "/parent" (data/to-bytes 1234) version)
    (data/to-long (:data (zk/data client "/parent")))
    ;; => 1234
```

The following types have been extended to support the to-bytes method: String, Integer, Double, Long, Float, Character. The following functions can be used to convert byte arrays back to their respective types: to-string, to-int, to-double, to-long, to-float, to-short, and to-char.

Clojure forms can be written to and read from the data field using pr-str and read-string, respectively.

```clojure
    (zk/set-data client "/parent" (data/to-bytes (pr-str {:a 1, :b 2, :c 3})) 2)
    (read-string (data/to-string (:data (zk/data client "/parent"))))
    ;; => {:a 1, :b 2, :c 3}
```

<a name="delete"></a>
### delete functions

Nodes can be deleted with the delete function.

```clojure
    (zk/delete client "/parent/child-node")
```

The delete function takes an optional version number, the delete will succeed if the node exists at the given version. the default version value is -1, which matches any version number.

The delete function accepts the :async? and :callback options.

Nodes that have children cannot be deleted. Two convenience functions, delete-children and delete-all, can be used to delete all of a node's children or delete a node and all of it's children, respectively.

```clojure
    (delete-all client "/parent")
```

<a name="acl"></a>
### ACL functions

The acl function takes a scheme, id value, and a set of permissions. The following schemes are built in.

* **world** has a single id, **anyone**, that represents anyone.
* **auth** doesn't use any id, represents any authenticated user.
* **digest** uses a username:password string to generate an MD5 hash which is then used as an ACL ID identity. Authentication is done by sending the username:password in clear text. When used in the ACL the expression will be the username:base64 encoded SHA1 password digest.
* **host** uses the client host name as an ACL ID identity. The ACL expression is a hostname suffix. For example, the ACL expression host:corp.com matches the ids host:host1.corp.com and host:host2.corp.com, but not host:host1.store.com.
* **ip** uses the client host IP as an ACL ID identity. The ACL expression is of the form addr/bits where the most significant bits of addr are matched against the most significant bits of the client host IP.

The folllowing permissions are supported:

* **:create**: you can create a child node
* **:read**: you can get data from a node and list its children.
* **:write**: you can set data for a node
* **:delete**: you can delete a child node
* **:admin**: you can set permissions

Below are examples of each ACL scheme.

```clojure
    (zk/acl "world" "anyone" :read :create :delete :admin :write)
    (zk/acl "ip" "127.0.0.1" :read :create :delete :admin :write)
    (zk/acl "host" "thinkrelevance.com" :admin :read :write :delete :create)
    (zk/acl "auth" "" :read :create :delete :admin :write)
```

There are five convenience functions for creating ACLs of each scheme, world-acl, auth-acl, digest-acl, host-acl, and ip-acl.

```clojure
    (zk/world-acl :read :delete :write)
```

When no permissions are provided, the following are used by default: :read, :create, :delete, :write -- but not :admin.
   
```clojure
    (zk/ip-acl "127.0.0.1")
    (zk/digest-acl "david:secret" :read :delete :write)
    (zk/host-acl "thinkrelevance.com" :read :delete :write)
    (zk/auth-acl :read :delete :write)
```

A list of ACLs can be passed as an option to the create function.

```clojure
    (zk/create client "/protected-node" :acl [(zk/auth-acl :admin :create :read :delete :write)])
```
  
In the above example, only the user that created the node has permissions on it. In order to authenticate a user, authentication info must be added to a client connection with the add-auth-info function.

```clojure
    (zk/add-auth-info client "digest" "david:secret")
```

If an unauthorized client tries to access the node, a org.apache.zookeeper.KeeperException$NoAuthException exception will be thrown.

<a name="group-membership"></a>
## Group Membership Example

```clojure
    (def group-name "/example-group")

    (def client (zk/connect "127.0.0.1:2181"))

    (when-not (zk/exists client group-name)
      (zk/create client group-name :persistent? true))
```

This watcher will be called every time the children of the
"/example-group" node are changed. Each time it is called it will
print the children and add itself as the watcher.

```clojure
    (defn group-watcher [x]
      (let [group (zk/children client group-name :watcher group-watcher)]
        (prn "Group members: " group)))
```

Create a new node for this member and add a watcher for changes to the
children of "/example-group".

```clojure
    (defn join-group [name]
      (do (zk/create client (str group-name "/" name))
          (zk/children client group-name :watcher group-watcher)))
```

### Run this Example

```clojure
    (use 'examples.group-membership)
    (join-group "bob")
```

From another REPL run:

```clojure
    (use 'examples.group-membership)
    (join-group "sue")
```

And from another REPL run:

```clojure
    (use 'examples.group-membership)
    (join-group "dan")
```

Each REPL will print the group members as each one joins the
group. Kill any process and the remaining processes will print the
remaining group members.

<a name="leader-election"></a>
## Leader Election Example

```clojure
    (def root-znode "/election")

    (def client (zk/connect "127.0.0.1:2181"))

    (when-not (zk/exists client root-znode)
      (zk/create client root-znode :persistent? true))

    (defn node-from-path [path]
      (.substring path (inc (count root-znode))))

    (declare elect-leader)
```

The predecessor for Node A is the node that has the highest id that is
< the id of Node A. watch-predecessor is called when the predecessor
node changes. If this node is deleted and was the leader, then the
watching node becomes the new leader.

```clojure
    (defn watch-predecessor [me pred leader {:keys [event-type path]}]
      (if (and (= event-type :NodeDeleted) (= (node-from-path path) leader))
        (println "I am the leader!")
        (if-not (zk/exists client (str root-znode "/" pred)
                           :watcher (partial watch-predecessor me pred leader))
          (elect-leader me))))

    (defn predecessor [me coll]
      (ffirst (filter #(= (second %) me) (partition 2 1 coll))))
```

If the node associated with the current process is not the leader then
add a watch to the predecessor.

```clojure
    (defn elect-leader [me]
      (let [members (util/sort-sequential-nodes (zk/children client root-znode))
            leader (first members)]
        (print "I am" me)
        (if (= me leader)
          (println " and I am the leader!")
          (let [pred (predecessor me members)]
            (println " and my predecessor is:" pred)
            (if-not (zk/exists client (str root-znode "/" pred)
                               :watcher (partial watch-predecessor me pred leader))
              (elect-leader me))))))

    (defn join-group []
      (let [me (node-from-path (zk/create client (str root-znode "/n-") :sequential? true))]
        (elect-leader me)))
```

Evaluate the following forms in any number of REPLs and then kill each one
in any order.

```clojure
    (use 'examples.leader-election)
    (join-group)
```
   
<a name="barrier"></a>
## Barrier Example

Distributed systems use barriers to block processing of a set of nodes until a condition is met at which time all the nodes are allowed to proceed.

The following is an implementation of a double barrier based on the algorithm from the <a href="http://zookeeper.apache.org/doc/trunk/recipes.html#sc_recipes_eventHandles">ZooKeeper Recipes</a> page. 

```clojure
    (require '[zookeeper :as zk])
    (import '(java.net InetAddress))

    (defn enter-barrier
      ([client n f & {:keys [barrier-node proc-name double-barrier?]
                      :or {barrier-node "/barrier"
                           proc-name (.getCanonicalHostName (InetAddress/getLocalHost))
                           double-barrier? true}}]
        (let [mutex (Object.)
              watcher (fn [event] (locking mutex (.notify mutex)))]
          (locking mutex
            (zk/create-all client (str barrier-node "/" proc-name))
            (if (>= (count (zk/children client barrier-node)) n)
              (zk/create client (str barrier-node "/ready") :async? true)
              (do (zk/exists client (str barrier-node "/ready") :watcher watcher :async? true)
                (.wait mutex)))
            (let [results (f)]
              (if double-barrier?
                (exit-barrier client :barrier-node barrier-node :proc-name proc-name)
                (zk/delete-all client barrier-node))
              results)))))
```

If the :double-barrier? option is set to true, then exit-barrier is called which blocks until all the processes have completed.

```clojure
    (defn exit-barrier
      ([client & {:keys [barrier-node proc-name]
                  :or {barrier-node "/barrier"
                       proc-name (.getCanonicalHostName (InetAddress/getLocalHost))}}]
        (let [mutex (Object.)
              watcher (fn [event] (locking mutex (.notify mutex)))]
          (zk/delete client (str barrier-node "/ready"))
          (locking mutex
            (loop []
              (when-let [children (seq (sort (or (zk/children client barrier-node) nil)))]
                (cond
                  ;; the last node deletes itself and the barrier node, letting all the processes exit
                  (= (count children) 1)
                    (zk/delete-all client barrier-node)
                  ;; first node watches the second, waiting for it to be deleted
                  (= proc-name (first children))
                    (do (when (zk/exists client
                                         (str barrier-node "/" (second children))
                                         :watcher watcher)
                          (.wait mutex))
                        (recur))
                  ;; rest of the nodes delete their own node, and then watch the
                  ;; first node, waiting for it to be deleted
                  :else
                    (do (zk/delete client (str barrier-node "/" proc-name))
                        (when (zk/exists client
                                         (str barrier-node "/" (first children))
                                         :watcher watcher)
                          (.wait mutex))
                        (recur)))))))))
```

### Example Usage

```clojure
    (require '[zookeeper :as zk])
    (use 'examples.barrier)
    (def client (zk/connect "127.0.0.1:2181"))

    (enter-barrier client 2 #(println "First process is running"))
```

The call to enter-barrier will block until there are N=2 processes in the barrier. From another REPL, execute the following, and then both processes will run and exit the barrier.

```clojure
    (require '[zookeeper :as zk])
    (use 'examples.barrier)
    (def client (zk/connect "127.0.0.1:2181"))

    (enter-barrier client 2 #(println "Second process is running") :proc-name "node2")
```


<a name="running-zookeeper"></a>
## Running ZooKeeper

Download Apache ZooKeeper from <a href="http://zookeeper.apache.org/releases.html">http://zookeeper.apache.org/releases.html</a>.

Unpack to $ZOOKEEPER_HOME (wherever you would like that to be).

Here's an example conf file for a standalone instance, by default ZooKeeper will look for it in $ZOOKEEPER_HOME/conf/zoo.cfg

```sh
    # The number of milliseconds of each tick
    tickTime=2000
    
    # the directory where the snapshot is stored.
    dataDir=/var/zookeeper
    
    # the port at which the clients will connect
    clientPort=2181
```
  
Ensure that the dataDir exists and is writable.
    
After creating and customizing the conf file, start ZooKeeper

```sh
    $ZOOKEEPER_HOME/bin/zkServer.sh start
```

<a name="testing"></a>
## Testing

Before running 'lein test' you need to start a local instance of ZooKeeper on port 2181.

<a name="contributing" />
## Contributing

Although Zookeeper-clj is not part of Clojure-Contrib, it follows the same guidelines for contributing, which includes signing a <a href="http://clojure.org/contributing">Clojure Contributor Agreement</a> (CA) before contributions can be accepted.

<a name="ref"></a>
## References

* <a href=" http://zookeeper.apache.org/">ZooKeeper Website</a>
* <a href="http://zookeeper.apache.org/doc/r3.3.3/zookeeperProgrammers.html">ZooKeeper Programming Guide</a>
* <a href="http://zookeeper.apache.org/doc/r3.3.3/api/index.html">ZooKeeper 3.3.3 API</a>
* <a href="http://wiki.apache.org/hadoop/ZooKeeper/Tutorial">ZooKeeper Tutorial</a>
* <a href="http://www.usenix.org/event/atc10/tech/full_papers/Hunt.pdf">ZooKeeper: Wait-free coordination for Internet-scale systems</a>
* <a href="http://zookeeper.apache.org/doc/trunk/recipes.html">ZooKeeper Recipes and Solutions</a>
* <a href="https://github.com/openUtility/menagerie">Menagerie Library</a>
* <a href="https://github.com/liebke/avout">Avout Library</a>


## License

zookeper-clj is Copyright © 2011 David Liebke and Relevance, Inc

Distributed under the Eclipse Public License, the same as Clojure.

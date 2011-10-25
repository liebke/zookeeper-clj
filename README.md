# zookeeper-clj

A Clojure DSL for Apache ZooKeeper.


## Tutorial

To run these examples, first start a local instance of ZooKeeper on port 2181, see <a href="#running-zookeeper">instructions below</a>.


### connect function

First require the zookeeper namespace and create a client with the connect function.

    (require '[zookeeper :as zk])
    (def client (zk/connect "127.0.0.1:2181"))
    
The connection string is the name, or IP address, and port of the ZooKeeper server. Several host:port pairs can be included as a comma seperated list. The port can be left off if it is 2181.


### watchers

A watcher function that takes a single event map argument can be passed to connect, which will be invoked as a result of changes of keeper-state, or as a result of other events. 

    (def client (zk/connect "127.0.0.1" :watcher (fn [event] (println event))))
    
if the :watch? flag is set to true when using the exists, children, or data functions, the default watcher function will be triggered under the following circumstances.

* exists: the watch will be triggered by a successful operation that creates/delete the node or sets the data on the node.
* children: the watch will be triggered by a successful operation that deletes the node of the given path or creates/delete a child under the node.
* data: the watch will be triggered by a successful operation that sets data on the node, or deletes the node.

The default watcher function can be overriden with a custom function by passing it as the :watcher argument to the exists, children, or data functions.

The argument to the watcher function is a map with three keys: :event-type, :keeper-state, and :path.

* event-type: :NodeDeleted, :NodeDataChanged, :NodeCreated, :NodeChildrenChanged, :None
* keeper-state: :AuthFailed, :Unknown, :SyncConnected, :Disconnected, :Expired, :NoSyncConnected
* path: the path the node in question, may be nil


### create function

Next, create a node called "/parent-node"

    (zk/create client "/parent-node" :persistent? true)
    ;; => "/parent-node"
    
Setting the :persistent? flag to true creates a persistent node, meaning one that will persist even after the client that created it is no longer connected. By default, nodes are ephemeral (i.e. :persistent? false) and will be deleted if the client that created them is disconnected (this is key to how ZooKeeper is used to build robust distributed systems).

A node must be persistent if you want it to have child nodes.


### asynchronous calls

Most of the zookeeper functions can be called asynchronously by setting the :async? option to true, or by providing an explicit callback function with the :callback option. When invoked asynchronously, each function will return a promise that will eventually contain the result of the call (a map with the following keys: :return-code, :path, :context, :name).

    (def result-promise (zk/create client "/parent-node" :persistent? true :async? true))
    
Dereferencing the promise will block until a result is returned.

    @result-promise

If a :callback function is passed, the promise will be returned with the result map and the callback will be invoked with the same map.

    (def result-promise (zk/create client "/parent-node" :persistent? true :callback (fn [result] (println result))))


### exists function

We can check the existence of the newly created node with the exists function.

    (zk/exists client "/parent-node")
    
The exists function returns nil if the node does not exist, and returns a map with the following keys if it does: :numChildren, :ephemeralOwner, :cversion, :mzxid, :czxid, :dataLength, :ctime, :version, :aversion, :mtime, :pzxid. See the ZooKeeper documentation for description of each field.

The exists function accepts the :watch?, :watcher, :async?, and :callback options. The watch functions will be triggered by a successful operation that creates/delete the node or sets the data on the node.


### children function

Next, create a child node for "/parent-node"

    (zk/create client "/parent-node/child-node")
    ;; => "/parent-node/child-node"
    
Since the :persistent? flag wasn't set to true, this node will be ephemeral, meaning it will be deleted if the client that created it is disconnected.

A list of a node's children can be retrieved with the children function.

    (zk/children client "/parent-node")
    ;; => ("child-node")
    
If the node has no children, nil will be returned, and if the node doesn't exist, false will be returned. 

The children function accepts the :watch?, :watcher, :async?, and :callback options. The watch function will be triggered by a successful operation that deletes the node of the given path or creates/delete a child under the node.


### sequential nodes

If the :sequential? option is set to true when a node is created, a ten digit sequential ID is appended to the name of the node (it's idiomatic to include a dash as the last character of a sequential node's name).

    (zk/create client "/parent-node/child-" :sequential? true)
    ;; => "/parent-node/child-0000000000"

    (zk/create client "/parent-node/child-" :sequential? true)
    ;; => "/parent-node/child-0000000001"

    (zk/create client "/parent-node/child-" :sequential? true)
    ;; => "/parent-node/child-0000000002"
    
The sequence ID increases monotonically for a given parent directory.


### data functions

Each node has a data field that can hold a byte array, which is limited to 1M is size. 

The set-data function is used to insert data. The set-data function takes a version number, which needs to match the current data version. The current version is a field in the map returned by the exists function.

    (dev version (:version (zk/exists client "/parent-node")))

    (zk/set-data client "/parent-node" (.getBytes "hello world" "UTF-8") version)
    
The data function is used to retrieve the data stored in a node.
    
    (zk/data client "/parent-node")
    ;; => {:data ..., :stat {...}}
    
The data function returns a map with two fields, :data and :stat. The :stat value is the same map returned by the exists function. The :data value is a byte array.

    (String. (:data (zk/data client "/parent-node")) "UTF-8")
    ;; => "hello world"

The data function accepts the :watch?, :watcher, :async?, and :callback options. The watch function will be triggered by a successful operation that sets data on the node, or deletes the node.

### data serialization

The zookeeper.data namespace contains functions for serializing different primitive types to and from byte arrays.

    (require '[zookeeper.data :as data])
    (zk/set-data client "/parent-node" (data/to-bytes 1234) version)
    (data/to-long (:data (zk/data client "/parent-node")))
    ;; => 1234

The following types have been extended to support the to-bytes method: String, Integer, Double, Long, Float, Character. The following functions can be used to convert byte arrays back to their respective types: to-string, to-int, to-double, to-long, to-float, to-short, and to-char.

Clojure forms can be written to and read from the data field using pr-str and read-string, respectively.

    (zk/set-data client "/parent-node" (data/to-bytes (pr-str {:a 1, :b 2, :c 3})) 2)
    (read-string (data/to-string (:data (zk/data client "/parent-node"))))
    ;; => {:a 1, :b 2, :c 3}


### delete functions

Nodes can be deleted with the delete function.

    (zk/delete client "/parent-node/child-node")
    
The delete function takes an optional version number, the delete will succeed if the node exists at the given version. the default version value is -1, which matches any version number.

The delete function accepts the :async? and :callback options.

Nodes that have children cannot be deleted. Two convenience functions, delete-children and delete-all, can be used to delete all of a node's children or delete a node and all of it's children, respectively.

    (delete-all client "/parent-node")


### ACL functions


### zookeeper.util

The zookeeper.util contains utilities that augment the functionality of the core namespace.


<a name="running-zookeeper"></a>
## Running ZooKeeper

Here's an example conf file for a standalone instance, by default ZooKeeper will look for it in $ZOOKEEPER_HOME/conf/zoo.cfg

    # The number of milliseconds of each tick
    tickTime=2000
    
    # the directory where the snapshot is stored.
    dataDir=/var/zookeeper
    
    # the port at which the clients will connect
    clientPort=2181
    
    
After creating and customizing the conf file, start ZooKeeper

    $ZOOKEEPER_HOME/bin/zkServer.sh start


## Testing

Before running 'lein test' you need to start a local instance of ZooKeeper on port 2181.


## References

* ZooKeeper http://zookeeper.apache.org/
* ZooKeeper: Wait-free coordination for Internet-scale systems http://www.usenix.org/event/atc10/tech/full_papers/Hunt.pdf

## License

Copyright (C) 2011 

Distributed under the Eclipse Public License, the same as Clojure.

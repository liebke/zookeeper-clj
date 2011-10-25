# zookeeper-clj

A Clojure DSL for Apache ZooKeeper.


## zookeeper

The primary namespace of the ZooKeeper DSL is zookeeper.

### Examples

To run these examples, first start a local instance of ZooKeeper on port 2181, see <a href="#running-zookeeper">instructions below</a>.

**connect function**

First require the zookeeper namespace and create a client with the connect function.

    (require '[zookeeper :as zk])
    (def client (zk/connect "127.0.0.1:2181"))
    
The connection string is the name, or IP address, and port of the ZooKeeper server. Several host:port pairs can be included as a comma seperated list. The port can be left off if it is 2181.

**watchers**

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

**create function**

Next, create a node called "/parent-node"

    (zk/create client "/parent-node" :persistent? true)
    
Setting the :persistent? flag to true creates a persistent node, meaning one that will persist even after the client that created it is no longer connected. By default, nodes are ephemeral (i.e. :persistent? false) and will be deleted if the client that created them is disconnected (this is key to how ZooKeeper is used to build robust distributed systems).

A node must be persistent if you want it to have child nodes.

**asynchronous calls**

Most of the zookeeper functions can be called asynchronously by setting the :async? option to true, or by providing an explicit callback function with the :callback option. When invoked asynchronously, each function will return a promise that will eventually contain the result of the call (a map with the following keys: :return-code, :path, :context, :name).

    (def result-promise (zk/create client "/parent-node" :persistent? true :async? true))
    
Dereferencing the promise will block until a result is returned.

    @result-promise

If a :callback function is passed, the promise will be returned with the result map and the callback will be invoked with the same map.

    (def result-promise (zk/create client "/parent-node" :persistent? true :callback (fn [result] (println result))))
    
**exists function**

We can check the existence of the newly created node with the exists function.

    (exists client "/parent-node")
    

**children function**

**data functions**

**delete functions**

**ACL functions**


## zookeeper.util

The zookeeper.util contains utilities that augment the functionality of the core namespace.


## Running ZooKeeper
<a name="running-zookeeper"></a>
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

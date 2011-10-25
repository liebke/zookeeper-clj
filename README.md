# zookeeper-clj

A Clojure DSL for Apache ZooKeeper.


## zookeeper

The primary namespace of the ZooKeeper DSL is zookeeper.


## zookeeper.util

The zookeeper.util contains utilities that augment the functionality of the core namespace.


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

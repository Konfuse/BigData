# Zookeeper3.4.13 配置

第一步，解压压缩包到`/usr/local`目录下，更名为zookeeper，并分配用户权限

```shell
sudo tar -zxvf zookeeper-3.4.13.tar.gz -C /usr/local
sudo mv zookeeper-3.4.13/ zookeeper
sudo chown -R konfuse zookeeper/
```

第二步，将zookeeper加入环境变量，在`/etc/profile`文件中加入zookeeper的PATH：

```shell
# set zookeeper environment
export ZOOKEEPER_HOME=/usr/local/zookeeper
export PATH=$PATH:$ZOOKEEPER_HOME/bin
```

​	刷新当前环境，或重启计算机：

```shell
source /etc/profile
```

第三步，创建zookeeper的核心文件`zoo.cfg`，初次使用 zookeeper 时，需要将 `$ZOOKEEPER_HOME/conf `目录下的 `zoo_sample.cfg `重命名为 `zoo.cfg`

```shell
cd /usr/local/zookeeper/conf
cp zoo_sample.cfg zoo.cfg
```

` zoo.cfg `作为zookeeper的配置文件，默认配置如下:

```shell
# The number of milliseconds of each 
ticktickTime=2000
# The number of ticks that the initial 
# synchronization phase can take
initLimit=10
# The number of ticks that can pass between 
# sending a request and getting an acknowledgement
syncLimit=5
# the directory where the snapshot is stored.
# do not use /tmp for storage, /tmp here is just 
# example sakes.dataDir=/tmp/zookeeper
# the port at which the clients will connect
clientPort=2181
# the maximum number of client connections.
# increase this if you need to handle more clients
#maxClientCnxns=60
#
# Be sure to read the maintenance section of the 
# administrator guide before turning on autopurge.
#
# http://zookeeper.apache.org/doc/current/zookeeperAdmin.html#sc_maintenance
#
# The number of snapshots to retain in dataDir
#autopurge.snapRetainCount=3
# Purge task interval in hours
# Set to "0" to disable auto purge feature
#autopurge.purgeInterval=1
```

**配置说明**：

- tickTime: ZooKeeper 中使用的基本时间单元，以毫秒为单位，默认值是 2000。它用来调节心跳和超时。例如，默认的会话超时时间是两倍的 tickTime。

- initLimit: 默认值是 10，即 tickTime 属性值的 10 倍。它用于配置允许 followers 连接并同步到 leader 的最大时间。如果 ZooKeeper 管理的数据量很大的话可以增加这个值。

- syncLimit: 默认值是 5，即 tickTime 属性值的 5 倍。它用于配置leader 和 followers 间进行心跳检测的最大延迟时间。如果在设置的时间内 followers 无法与 leader 进行通信，那么 followers 将会被丢弃。

- dataDir: ZooKeeper 用来存储内存数据库快照的目录，并且除非指定其它目录，否则数据库更新的事务日志也将会存储在该目录下。建议配置 dataLogDir 参数来指定 ZooKeeper 事务日志的存储目录。

- clientPort: 服务器监听客户端连接的端口，也即客户端尝试连接的端口，默认值是 2181。

- maxClientCnxns: 在 socket 级别限制单个客户端与单台服务器之前的并发连接数量，可以通过 IP 地址来区分不同的客户端。它用来防止某种类型的 DoS 攻击，包括文件描述符耗尽。默认值是 60。将其设置为 0 将完全移除并发连接数的限制。

- autopurge.snapRetainCount: 配置 ZooKeeper 在自动清理的时候需要保留的数据文件快照的数量和对应的事务日志文件，默认值是 3。

- autopurge.purgeInterval: 和参数 autopurge.snapRetainCount 配套使用，用于配置 ZooKeeper 自动清理文件的频率，默认值是 1，即默认开启自动清理功能，设置为 0 则表示禁用自动清理功能。

## 一. 单机模式

### 1. zoo.cfg配置

```shell
ticketTime=2000
clientPort=2181
dataDir=/usr/local/zookeeper/data
dataLogDir=/usr/local/zookeeper/logs
```

### 2. 启动zookeeper服务

```shell
zkServer.sh start
```

### 3. 验证服务是否成功启动

使用telnet 和 stat 命令验证服务是否成功启动

```shell
telnet 127.0.0.1 2181
```

​	在单机模式中，Mode 的值是 "standalone"。

### 4. 停止zookeeper服务

```shell
zkServer.sh stop
```

## 二. 集群模式

### 1. zoo.cfg配置

在单机模式的配置文件下增加了最后 5 行配置:

```shell
ticketTime=2000
clientPort=2181
dataDir=/usr/local/zookeeper/data
dataLogDir=/usr/local/zookeeper/logs
initLimit=10
syncLimit=5
server.1=master:2888:3888
server.2=slaver1:2888:3888
server.3=slaver2:2888:3888
```

**配置说明：**

- 集群模式中，集群中的每台机器都需要感知其它机器， 在 `zoo.cfg `配置文件中，可以按照如下格式进行配置，每一行代表一台服务器配置:

  ```shell
  server.id=host:port:port
  ```

- id 被称为 Server ID，用来标识服务器在集群中的序号。同时每台 ZooKeeper 服务器上，都需要在数据目录(即 dataDir 指定的目录) 下创建一个 `myid `文件，该文件只有一行内容，即对应于每台服务器的Server ID。
- ZooKeeper 集群中，每台服务器上的` zoo.cfg` 配置文件内容一致。
- server.1 的` myid `文件内容就是 "1"。每个服务器的 myid 内容都不同，且需要保证和自己的 `zoo.cfg` 配置文件中 `server.id=host:port:port `的 id 值一致。
- id 的范围是 1 ~ 255。

### 2. 创建myid文件

在 dataDir 指定的目录下 (即 `/usr/local/zookeeper/data `目录) 创建名为` myid `的文件，文件内容和` zoo.cfg` 中当前机器的 id 一致。根据上述配置, master 的 `myid `文件内容为 1。

```shell
cd /usr/local/zookeeper
vim myid
```

### 3. slave配置

按照相同步骤，为 slaver1 和 slaver2 配置 `zoo.cfg `和` myid `文件。`zoo.cfg`文件内容相同，slaver1 的 `myid `文件内容为 2，slaver2 的` myid `文件内容为 3。

### 4. 启动zookeeper服务

zookeeper的启动需要到各个机器上单独进行启动,具体启动命令如下:

```shell
zkServer.sh start
```

启动后使用jps查看进程,如果看到QuorumPeerMain进程，说明这个机器上的zookeeper就启动了。

同理，依次启动所有机器上的zookeeper。

### 5. 查看zookeeper状态

接着每个机器可以使用命令查看自己的状态:

```shell
zkServer.sh status
```

### 6. 停止zookeeper服务

zookeeper集群一般无需关闭,其相互间既是整体，也是单独的个体。

```shell
zkServer.sh stop
```

**注意：**有时候使用zookeeper启动不起来，`lsof -i:2181`发现2181端口被进程占用，但是进程没有pid，原因是不同的用户启动了zookeeper(使用root用户可以看到所有)。
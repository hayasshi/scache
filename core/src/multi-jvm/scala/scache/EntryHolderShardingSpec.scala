package scache

import java.io.File
import java.time.Instant

import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.cluster.{Cluster, MemberStatus}
import akka.remote.testconductor.RoleName
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import scache.EntryHolder.Protocol.{GetRequest, GetSuccess, SetRequest, SetSuccess}

import scala.concurrent.duration._

object MultiNodeShardingConfig extends MultiNodeConfig {
  // Reference for cluster multi node testing. see: https://doc.akka.io/docs/akka/2.5/cluster-usage.html#how-to-test
  val node1: RoleName = role("node1")
  val node2: RoleName = role("node2")
  val node3: RoleName = role("node3")
  val node4: RoleName = role("node4")

  def nodeList: Seq[RoleName] = Seq(node1, node2, node3, node4)

  // see: https://github.com/akka/akka/blob/bdc90052aac7936b9d2c800c01b0da4d0653c781/akka-cluster/src/multi-jvm/scala/akka/cluster/MultiNodeClusterSpec.scala
  commonConfig(ConfigFactory.parseString(s"""
    akka.actor.provider = cluster
    akka.actor.warn-about-java-serializer-usage = off
    akka.cluster {
      jmx.enabled                         = off
      gossip-interval                     = 200 ms
      leader-actions-interval             = 200 ms
      unreachable-nodes-reaper-interval   = 500 ms
      periodic-tasks-initial-delay        = 300 ms
      publish-stats-interval              = 0 s # always, when it happens
      failure-detector.heartbeat-interval = 500 ms
      run-coordinated-shutdown-when-down  = off
      min-nr-of-members = 3
      sharding {
        state-store-mode = "ddata"
        rebalance-interval = 10s
        retry-interval = 200ms
        waiting-for-state-timeout = 200ms
        distributed-data.durable.lmdb {
          dir = target/EntryHolderShardingSpec/sharding-ddata
          map-size = 10 MiB
        }
      }
    }
    akka.loglevel = INFO
    akka.log-dead-letters = off
    akka.log-dead-letters-during-shutdown = off
    akka.remote {
      enabled = on
      transport = tcp
      log-remote-lifecycle-events = off
    }
    akka.loggers = ["akka.testkit.TestEventListener"]
    akka.test {
      single-expect-default = 5 s
    }
    """))
}

class EntryHolderShardingSpecMultiJvmNode1 extends EntryHolderShardingSpec
class EntryHolderShardingSpecMultiJvmNode2 extends EntryHolderShardingSpec
class EntryHolderShardingSpecMultiJvmNode3 extends EntryHolderShardingSpec
class EntryHolderShardingSpecMultiJvmNode4 extends EntryHolderShardingSpec

class EntryHolderShardingSpec
    extends MultiNodeSpec(MultiNodeShardingConfig)
    with STMultiNodeSpec
    with ImplicitSender {
  import MultiNodeShardingConfig._

  def initialParticipants: Int = roles.size

  val storageLocations = List(new File(system.settings.config.getString(
    "akka.cluster.sharding.distributed-data.durable.lmdb.dir")).getParentFile)

  override protected def atStartup(): Unit = {
    storageLocations.foreach(dir ⇒ if (dir.exists) FileUtils.deleteQuietly(dir))
    enterBarrier("startup")
  }

  override protected def afterTermination(): Unit = {
    storageLocations.foreach(dir ⇒ if (dir.exists) FileUtils.deleteQuietly(dir))
  }

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
    }
    enterBarrier(from.name + "-joined")
  }

  val cluster: Cluster = Cluster(system)

  def startSharding(): Unit = {
    ClusterSharding(system).start(
      typeName = "CacheEntry",
      entityProps = EntryHolder.props(),
      settings = ClusterShardingSettings(system).withRememberEntities(true),
      extractEntityId = {
        case req: SetRequest => (req.entry.key.utf8String, req)
        case req: GetRequest => (req.key.utf8String, req)
      },
      extractShardId = {
        case req: SetRequest => (req.entry.key.utf8String.toLong % 10).toString
        case req: GetRequest => (req.key.utf8String.toLong % 10).toString
        case ShardRegion.StartEntity(id) => (id.toLong % 10).toString
      }
    )
  }

  "A EntryHolder on cluster sharding" should {

    "wait for all nodes to enter a barrier" in {
      enterBarrier("startup")
    }

    "join cluster and start cluster sharding" in within(30.seconds) {
      join(node1, node1)
      runOn(node1) {
        startSharding()
      }
      join(node2, node1)
      runOn(node2) {
        startSharding()
      }
      join(node3, node1)
      runOn(node3) {
        startSharding()
      }
      join(node4, node1)
      runOn(node4) {
        startSharding()
      }
      within(remaining) {
        awaitAssert {
          cluster.state.members.size should ===(nodeList.size)
          cluster.state.members.map(_.status) should ===(Set(MemberStatus.Up))
        }
      }
      enterBarrier("joined cluster")
    }

    val storedEntries = (1 to 10).map { id =>
      Entry(ByteString(id.toString), ByteString(s"value:$id"), Instant.now, Instant.now.plusSeconds(60))
    }

    "store entries" in {
      runOn(node1) {
        val shardRegion = ClusterSharding(system).shardRegion("CacheEntry")
        storedEntries.foreach { entry =>
          val req = SetRequest(entry)
          shardRegion ! req
          expectMsgType[SetSuccess](30.seconds).request shouldBe req
        }
      }
      enterBarrier("store finished")
    }

    "be able to get entries" in {
      runOn(node2) {
        val shardRegion = ClusterSharding(system).shardRegion("CacheEntry")
        storedEntries.foreach { entry =>
          val req = GetRequest(entry.key)
          shardRegion ! req
          val stored = expectMsgType[GetSuccess](30.seconds)
          stored.request shouldBe req
          stored.entry.key shouldBe entry.key
          stored.entry.value shouldBe entry.value
          // Time is staggered, because 'Entry' is initialized on multi jvm testing.
        }
      }
    }

    "leave node from cluster" in within(30.seconds) {
      runOn(node2) {
        val cluster = Cluster(system)
        cluster leave node(node2).address
        cluster down node(node2).address
      }
      enterBarrier("now removing")

      runOn(node1, node3, node4) {
        within(remaining) {
          awaitAssert {
            cluster.state.members.size should ===(nodeList.size - 1)
            cluster.state.members.map(_.status) should ===(Set(MemberStatus.Up))
          }
        }
      }
      enterBarrier("finished removing")
    }

    // FixMe: Fix ddata configuration for restore data
    "not less entry" ignore within(30.seconds) {
      runOn(node1) {
        val shardRegion = ClusterSharding(system).shardRegion("CacheEntry")
        storedEntries.foreach { entry =>
          val req = GetRequest(entry.key)
          within(remaining) {
            awaitAssert {
              shardRegion ! req
              expectMsgType[GetSuccess](30.seconds) shouldBe GetSuccess(req, entry)
            }
          }
        }
      }
      enterBarrier("lost check finished")
    }

  }

}

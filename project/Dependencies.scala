import sbt._

object Dependencies {

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5"

  lazy val apacheCommonsIo = "commons-io" % "commons-io" % "2.6"

  object Akka {
    val version = "2.5.17"

    lazy val actor   = "com.typesafe.akka" %% "akka-actor"   % version
    lazy val cluster = "com.typesafe.akka" %% "akka-cluster" % version
    lazy val ddata   = "com.typesafe.akka" %% "akka-distributed-data" % version
    lazy val clusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % version
    lazy val clusterMetrics  = "com.typesafe.akka" %% "akka-cluster-metrics"  % version

    lazy val testKit          = "com.typesafe.akka" %% "akka-testkit" % version
    lazy val multiNodeTestKit = "com.typesafe.akka" %% "akka-multi-node-testkit" % version
  }

}

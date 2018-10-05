import Dependencies._
import com.typesafe.sbt.MultiJvmPlugin
import com.typesafe.sbt.MultiJvmPlugin.MultiJvmKeys.MultiJvm

lazy val coreSetting = Seq(
  organization := "com.github.hayasshi",
  scalaVersion := "2.12.7",
  version      := "0.1.0-SNAPSHOT",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-encoding",
    "UTF-8",
    "-Xfatal-warnings",
    "-language:existentials",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-language:higherKinds",
    "-Ywarn-adapted-args",     // Warn if an argument list is modified to match the receiver
    "-Ywarn-dead-code",        // Warn when dead code is identified.
    "-Ywarn-inaccessible",     // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any",        // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'
    "-Ywarn-nullary-unit",     // Warn when nullary methods return Unit.
    "-Ywarn-numeric-widen",    // Warn when numerics are widened.
  ),
)

lazy val root = (project in file("."))
  .settings(
    name := "scache",
  )
  .aggregate(core)

lazy val core = (project in file("core"))
  .settings(coreSetting)
  .settings(
    name := "scache-core",
    libraryDependencies ++= Seq(
      Akka.actor,
      Akka.clusterSharding,
      Akka.clusterMetrics,
      scalaTest % Test,
      Akka.testKit % Test,
      Akka.multiNodeTestKit % Test,
    )
  )
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)

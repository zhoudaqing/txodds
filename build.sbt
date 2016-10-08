lazy val buildSettings = Seq(
  organization := "zainab-ali",
  scalaVersion := "2.11.8",
  name         := "txodds",
  version      := "0.1.0-SNAPSHOT"
)

lazy val commonScalacOptions = Seq(
  "-encoding", "UTF-8",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps"
)

lazy val catsVersion = "0.7.0"
lazy val akkaVersion = "2.4.11"

lazy val commonSettings = Seq(
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots"),
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  ),
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.typelevel" %% "cats-macros" % catsVersion,
    "org.typelevel" %% "cats-kernel" % catsVersion,
    "org.scodec" %% "scodec-bits" % "1.1.1",
    "org.scodec" %% "scodec-core" % "1.10.2",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "org.scalacheck" %% "scalacheck" % "1.13.2" % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test"
  )
)

lazy val core = (project in file("core")).settings(
  moduleName := "core",
  commonSettings,
  buildSettings
)

lazy val writer = (project in file("writer")).settings(
  moduleName := "writer",
  commonSettings,
  buildSettings
).dependsOn(core)

lazy val reader = (project in file("reader")).settings(
  moduleName := "reader",
  commonSettings,
  buildSettings
).dependsOn(core)

lazy val server = (project in file("server")).settings(
  moduleName := "server",
  commonSettings,
  buildSettings
).dependsOn(core)

lazy val root = (project in file(".")).settings(
  buildSettings,
  scalacOptions ++= commonScalacOptions
).aggregate(core, writer, reader, server)

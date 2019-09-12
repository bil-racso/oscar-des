scalaVersion := "2.12.10"

name := "oscar-des"
organization := "oscarlib"
version := "4.1.0"

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xdisable-assertions",
  "-language:implicitConversions",
  "-language:postfixOps"
)

libraryDependencies += "net.sf.jsci" % "jsci" % "1.2"

ThisBuild / scalaVersion := "2.13.6"

val someOtherRef = ProjectRef(IO.toURI(file("some-other")), "root")
val someOtherLib = "com.example" % "some-other" % "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "hello"
  )

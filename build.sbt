import Dependencies._

inThisBuild(
  List(
    organization := "io.github.eleven19",
    homepage := Some(url("https://github.com/eleven19/sbt-hot-sources")),
    licenses := List("Apache-2.0" -> url("https://github.com/Eleven19/sbt-hot-sources/blob/main/LICENSE")),
    developers := List(
      Developer(
        "DamianReeves",
        "Damian Reeves",
        "957246+DamianReeves@users.noreply.github.com",
        url("https://damianreeves.github.io")
      )
    )
  )
)

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-hot-sources",
    description := " An sbt plugin that allows you to swap out dependencies for source dependencies when you want without breaking the build.",
    scalacOptions := Seq("-deprecation", "-unchecked"),
    publishMavenStyle := false,
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.5.4"
      }
    },
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio-config" % V.zioConfig,
      "dev.zio" %% "zio-config-magnolia" % V.zioConfig
    )
  )

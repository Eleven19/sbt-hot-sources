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
    ),
    testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )
)

lazy val sbtHotSources = (project in file("sbt-hot-sources"))
  .enablePlugins(SbtPlugin)
  .dependsOn(sbtHotSourcesConfig)
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
      "dev.zio" %% "zio" % V.zio,
      "dev.zio" %% "zio-json" % V.zioJson,
      "dev.zio" %% "zio-config" % V.zioConfig,
      "dev.zio" %% "zio-config-magnolia" % V.zioConfig,
      "dev.zio" %% "zio-test" % V.zio % Test,
      "dev.zio" %% "zio-test-sbt" % V.zio % Test
    )
  )

lazy val sbtHotSourcesConfig = (project in file("sbt-hot-sources-config"))
  .settings(
    name := "sbt-hot-sources-config",
    scalacOptions := Seq("-deprecation", "-unchecked")
  )
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % V.zio,
      "dev.zio" %% "zio-json" % V.zioJson,
      "dev.zio" %% "zio-config" % V.zioConfig,
      "dev.zio" %% "zio-config-magnolia" % V.zioConfig,
      "dev.zio" %% "zio-test" % V.zio % Test,
      "dev.zio" %% "zio-test-sbt" % V.zio % Test
    )
  )

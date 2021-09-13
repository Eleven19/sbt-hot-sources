ThisBuild / scalaVersion := "2.13.6"

lazy val root = (project in file("."))
  .settings(
    name := "foo",
    TaskKey[Unit]("check") := {
      import com.eed3si9n.expecty.Expecty.assert

      val baseDirectory = Keys.baseDirectory.value
      val hotSourcesConfigDir = baseDirectory / ".hotSources"

      assert(hotSourcesConfigDir.exists)

    }
  )

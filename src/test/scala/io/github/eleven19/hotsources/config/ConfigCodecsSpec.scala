package io.github.eleven19.hotsources.config
import java.nio.file.Paths
import zio.test._

object ConfigCodecsSpec extends DefaultRunnableSpec {
  def spec = suite("ConfigCodecsSpec")(
    suite("ConfigCodecs")(
      test("should support encoding a Config.File as a JSON encoded string") {
        val file = Config.File.fromProject(
          Config.Project("foo", Paths.get("/tmp/workspace/foo"), Some(Paths.get("/tmp/workspace/foo")))
        )

        assertTrue(
          ConfigCodecs.toStr(
            file,
            None
          ) == """{"version":"0.1.0","project":{"name":"foo","directory":"/tmp/workspace/foo","workspaceDir":"/tmp/workspace/foo"}}"""
        )
      }
    )
  )
}

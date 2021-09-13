package io.github.eleven19.hotsources.config

import java.nio.file.Path
object Config {

  final case class File(version: String, project: Project)
  object File {
    final val LatestVersion = "0.1.0"

    def fromProject(project: Project): File = File(version = LatestVersion, project = project)
  }

  final case class Project(
      name: String,
      directory: Path,
      workspaceDir: Option[Path],
      resolution: Option[Resolution]
  )
  object Project {}

  case class Checksum(
      `type`: String,
      digest: String
  )

  case class Artifact(
      name: String,
      classifier: Option[String],
      checksum: Option[Checksum],
      path: Path
  )

  final case class Module(
      organization: String,
      name: String,
      version: String,
      configurations: Option[String],
      artifacts: List[Artifact]
  )

  object Module {
    private[hotsources] val empty: Module = Module("", "", "", None, Nil)
  }

  case class Resolution(
      modules: List[Module]
  )
}

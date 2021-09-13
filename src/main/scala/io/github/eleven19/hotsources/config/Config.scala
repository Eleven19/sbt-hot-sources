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
      workspaceDir: Option[Path]
  )
  object Project {}

}

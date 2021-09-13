package io.github.eleven19.hotsources.config

import zio.json._

object Config {

  final case class File(version: String, project: Project)
  object File {
    final val LatestVersion = "0.1.0"
    implicit val decoder: JsonDecoder[File] = DeriveJsonDecoder.gen[File]
    implicit val encoder: JsonEncoder[File] = DeriveJsonEncoder.gen[File]

    def fromProject(project: Project): File = File(version = LatestVersion, project = project)
  }

  final case class Project(name: String)
  object Project {
    implicit val decoder: JsonDecoder[Project] = DeriveJsonDecoder.gen[Project]
    implicit val encoder: JsonEncoder[Project] = DeriveJsonEncoder.gen[Project]
  }

}

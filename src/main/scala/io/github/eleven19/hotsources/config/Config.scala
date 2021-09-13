package io.github.eleven19.hotsources.config

object Config {

  final case class File(version: String)
  object File {
    final val LatestVersion = "0.1."
  }

  final case class Project()
  object Project {}

}

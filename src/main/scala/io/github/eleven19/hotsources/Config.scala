package io.github.eleven19.hotsources

object Config {
  final case class File(version: String)
  object File {
    final val LatestVersion = "0.1."
  }
}

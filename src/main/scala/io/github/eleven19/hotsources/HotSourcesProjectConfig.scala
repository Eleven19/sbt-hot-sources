package io.github.eleven19.hotsources

import java.io.File

final case class HotSourcesProjectConfig(target: File, config: Config.File)
object HotSourcesProjectConfig {
  implicit val config = ???
}

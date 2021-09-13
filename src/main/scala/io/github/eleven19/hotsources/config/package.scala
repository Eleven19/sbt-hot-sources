package io.github.eleven19.hotsources

import java.nio.file.Path

package object config {
  def read(jsonConfig: Path): Either[Throwable, Config.File] = {
    //TODO: Actually read the thing
    Right(Config.File(Config.File.LatestVersion))
  }
}

package io.github.eleven19.hotsources

import java.nio.charset.StandardCharsets

import java.nio.file.{Files, Path}
import config.Config.File
package object config {
  def write(all: File): String = ConfigCodecs.toStr(all)
  def write(all: File, target: Path): Unit = {
    Files.write(target, write(all).getBytes(StandardCharsets.UTF_8))
    ()
  }

  def read(bytes: Array[Byte]): Either[Throwable, Config.File] = ConfigCodecs.read(bytes)
  def read(jsonConfig: Path): Either[Throwable, Config.File] = {
    ConfigCodecs.read(jsonConfig)
  }

//   def read(jsonConfig: Path): Either[Throwable, Config.File] = {
//     //TODO: Actually read the thing
//     Right(Config.File(Config.File.LatestVersion, Config.Project()))
//   }
}

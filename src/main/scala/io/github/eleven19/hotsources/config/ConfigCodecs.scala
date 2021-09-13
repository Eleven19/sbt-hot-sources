package io.github.eleven19.hotsources.config
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import io.github.eleven19.hotsources.config.Config._
import zio.json._
import java.nio.ByteBuffer

object ConfigCodecs {
  def read(configDir: Path): Either[Throwable, Config.File] = {
    read(Files.readAllBytes(configDir))
  }

  def read(bytes: Array[Byte]): Either[Throwable, Config.File] = {
    val charBuffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes))
    implicitly[JsonDecoder[Config.File]].decodeJson(charBuffer) match {
      case Left(errorMessage) => Left(DecodeError(errorMessage))
      case Right(file)        => Right(file)
    }

  }
  def toStr(all: File, indent: Option[Int] = Some(0)): String = {
    implicitly[JsonEncoder[File]].encodeJson(all, indent = indent).toString
  }

  final case class DecodeError(errorMessage: String)
      extends RuntimeException(s"A decoding error was encountered: $errorMessage")
}

package io.github.eleven19.hotsources.config
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import io.github.eleven19.hotsources.config.Config._
import zio.json._
import java.nio.ByteBuffer
import scala.util.Try
import scala.util.control.NonFatal
object ConfigCodecs {

  implicit val pathDecoder: JsonDecoder[Path] = JsonDecoder.string.mapOrFail { s =>
    try {
      Right(Paths.get(s))
    } catch {
      case NonFatal(error) =>
        Left(error.getMessage())
    }
  }

  implicit val pathEncoder: JsonEncoder[Path] = JsonEncoder.string.contramap[Path](path => path.toString())

  implicit val checksumEncoder: JsonEncoder[Checksum] = DeriveJsonEncoder.gen[Checksum]
  implicit val chesksumDecoder: JsonDecoder[Checksum] = DeriveJsonDecoder.gen[Checksum]

  implicit val artifactEncoder: JsonEncoder[Artifact] = DeriveJsonEncoder.gen[Artifact]
  implicit val artifactDecoder: JsonDecoder[Artifact] = DeriveJsonDecoder.gen[Artifact]

  implicit val moduleEncoder: JsonEncoder[Module] = DeriveJsonEncoder.gen[Module]
  implicit val moduleDecoder: JsonDecoder[Module] = DeriveJsonDecoder.gen[Module]

  implicit val resolutionEncoder: JsonEncoder[Resolution] = DeriveJsonEncoder.gen[Resolution]
  implicit val resolutionDecoder: JsonDecoder[Resolution] = DeriveJsonDecoder.gen[Resolution]

  implicit def projectEncoder(implicit pathEncoder: JsonEncoder[Path]): JsonEncoder[Project] =
    DeriveJsonEncoder.gen[Project]
  implicit def projectDecoder(implicit pathDecoder: JsonDecoder[Path]): JsonDecoder[Project] =
    DeriveJsonDecoder.gen[Project]

  implicit val allEncoder: JsonEncoder[File] = DeriveJsonEncoder.gen[File]
  implicit val allDecoder: JsonDecoder[File] = DeriveJsonDecoder.gen[File]

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

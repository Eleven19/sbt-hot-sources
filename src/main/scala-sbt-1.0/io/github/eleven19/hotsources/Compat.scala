package io.github.eleven19.hotsources

import sbt.io.syntax.File
import sbt.{Artifact, Exec, Keys, SettingKey, Def}
import sbt.librarymanagement.ScalaModuleInfo

object Compat {
  type CompileAnalysis = xsbti.compile.CompileAnalysis
  type PluginData = sbt.PluginData
  val PluginData = sbt.PluginData
  val PluginDiscovery = sbt.internal.PluginDiscovery
  val PluginManagement = sbt.internal.PluginManagement
  type CompileResult = xsbti.compile.CompileResult

  def currentCommandFromState(s: sbt.State): Option[String] =
    s.currentCommand.map(_.commandLine)

  implicit def execToString(e: Exec): String = e.commandLine

  implicit def fileToRichFile(file: File): sbt.RichFile = new sbt.RichFile(file)

  private final val anyWriter = implicitly[sbt.util.OptJsonWriter[AnyRef]]
  def toAnyRefSettingKey(id: String, m: Manifest[AnyRef]): SettingKey[AnyRef] =
    SettingKey(id)(m, anyWriter)

  import sbt.Task
  def cloneTask[T](task: Task[T]): Task[T] = {
    task.copy(
      info = task.info.setName("randooooooooooom1"),
      work = {
        task.work match {
          case sbt.DependsOn(in, deps) => sbt.DependsOn(in, deps)
          case w: sbt.Mapped[t, k]     => sbt.Mapped[t, k](w.in, w.f, w.alist)
          case w: sbt.FlatMapped[t, k] => sbt.FlatMapped[t, k](w.in, w.f, w.alist)
          case sbt.Join(in, f)         => sbt.Join(in, f)
          case sbt.Pure(f, inline)     => sbt.Pure(f, inline)
        }
      }
    )
  }
}

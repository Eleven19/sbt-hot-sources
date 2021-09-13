package io.github.eleven19.hotsources

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import io.github.eleven19._
import hotsources.config.{Config, Tag}

import sbt.{
  AutoPlugin,
  ClasspathDep,
  ClasspathDependency,
  Compile,
  ConfigKey,
  Configuration,
  Def,
  File,
  Global,
  Keys,
  LocalRootProject,
  Logger,
  ProjectRef,
  ResolvedProject,
  Test,
  IntegrationTest,
  ThisBuild,
  ThisProject,
  KeyRanks,
  Optional,
  Provided
}
import scala.util.{Try, Success, Failure}
import java.util.concurrent.ConcurrentHashMap
import java.nio.file.StandardCopyOption

object HotSourcesPlugin extends AutoPlugin {
  import sbt.plugins.JvmPlugin
  override def requires = JvmPlugin
  override def trigger = allRequirements

  final val autoImport = HotSourcesKeys

  override def buildSettings: Seq[Def.Setting[_]] = HotSourcesDefaults.buildSettings
  override def globalSettings: Seq[Def.Setting[_]] = HotSourcesDefaults.globalSettings
  override def projectSettings: Seq[Def.Setting[_]] = HotSourcesDefaults.projectSettings
}

object HotSourcesKeys {
  import sbt.{InputKey, SettingKey, TaskKey, AttributeKey, ScopedKey, inputKey, settingKey, taskKey}

  val hotSourcesTargetName: SettingKey[String] = settingKey[String]("Hot sources target name.")
  val hotSourcesConfigDir: SettingKey[File] =
    settingKey[File]("Directory where to write hot sources configuration files")
  val hotSourcesIsMetaBuild: SettingKey[Boolean] = settingKey[Boolean]("Is this a meta build?")
  val hotSourcesAggregateSourceDependencies: SettingKey[Boolean] = settingKey(
    "Flag to tell hot source to aggregate its config files in the same hot sources dir"
  )
  val hotSourcesExportJarClassifiers: SettingKey[Option[Set[String]]] =
    settingKey[Option[Set[String]]](
      "The classifiers that will be exported with `updateClassifiers`"
    )
  val hotSourcesProductDirectories: TaskKey[Seq[File]] =
    taskKey[Seq[File]]("Hot sources product directories")

  val hotSourcesTargetDir: SettingKey[File] =
    settingKey[File]("Target directory for the pertinent project and configuration")
  val hotSourcesInternalClasspath: TaskKey[Seq[(File, File)]] =
    taskKey[Seq[(File, File)]]("Directory where to write the class files")
  val hotSourcesInstall: TaskKey[Unit] = taskKey[Unit]("Generate all hot sources configuration files.")
  val hotSourcesGenerate: TaskKey[Option[File]] =
    taskKey[Option[File]]("Generate hot sources configuration for this project")
  val hotSourcesSetEnabled: InputKey[Unit] =
    inputKey[Unit]("Set the hot sources to enabled or disable for the build or project")

  val hotSourcesGlobalUniqueId: SettingKey[String] =
    sbt.SettingKey[String](
      "hotSourcesGlobalUniqueId",
      "Hot sources global unique id to represent a compiled settings universe",
      KeyRanks.Invisible
    )
}

object HotSourcesDefaults {
  import Compat._
  import sbt.{Task, Defaults, State}

  val productDirectoriesUndeprecatedKey =
    sbt.TaskKey[Seq[File]]("productDirectories", rank = KeyRanks.CTask)

  private lazy val cwd: String = System.getProperty("user.dir")
  val globalSettings: Seq[Def.Setting[_]] = List(
    HotSourcesKeys.hotSourcesGlobalUniqueId := hotSourcesGlobalUniqueIdTask.value,
    HotSourcesKeys.hotSourcesExportJarClassifiers := {
      Option(System.getProperty("hotSources.export-jar-classifiers"))
        .orElse(Option(System.getenv("HOT_SOURCES_EXPORT_JAR_CLASSIFIERS")))
        .map(_.split(",").toSet)
    },
    HotSourcesKeys.hotSourcesInstall := hotSourcesInstall.value,
    HotSourcesKeys.hotSourcesAggregateSourceDependencies := true,
    // Override classifiers so that we don't resolve always docs
    Keys.transitiveClassifiers in Keys.updateClassifiers := {
      val old = (Keys.transitiveClassifiers in Keys.updateClassifiers).value
      val bloopClassifiers = HotSourcesKeys.hotSourcesExportJarClassifiers.in(ThisBuild).value
      (if (bloopClassifiers.isEmpty) old else bloopClassifiers.get).toList
    },
    HotSourcesKeys.hotSourcesIsMetaBuild := {
      val buildStructure = Keys.loadedBuild.value
      val baseDirectory = new File(buildStructure.root)
      //TODO: Check if this is really needed as Bloop might have done this to allow bloop to be built with bloop
      val isMetaBuild = Keys.sbtPlugin.in(LocalRootProject).value
      isMetaBuild && baseDirectory.getAbsolutePath() != cwd
    },
    Keys.onLoad := {
      val oldOnLoad = Keys.onLoad.value
      oldOnLoad.andThen { state =>
        val isMetaBuild = HotSourcesKeys.hotSourcesIsMetaBuild.value
        if (!isMetaBuild) state
        else runCommandAndRemaining("hotSourcesInstall")(state)
      }
    }
  )

  // From the infamous https://stackoverflow.com/questions/40741244/in-sbt-how-to-execute-a-command-in-task
  def runCommandAndRemaining(command: String): State => State = { st: State =>
    import sbt.complete.Parser
    @annotation.tailrec
    def runCommand(command: String, state: State): State = {
      val nextState = Parser.parse(command, state.combinedParser) match {
        case Right(cmd) => cmd()
        case Left(msg)  => throw sys.error(s"Invalid programmatic input:\n$msg")
      }
      nextState.remainingCommands match {
        case Nil          => nextState
        case head :: tail => runCommand(head, nextState.copy(remainingCommands = tail))
      }
    }
    runCommand(command, st.copy(remainingCommands = Nil))
      .copy(remainingCommands = st.remainingCommands)
  }

  // We create build setting proxies to global settings so that we get autocompletion (sbt bug)
  val buildSettings: Seq[Def.Setting[_]] = List(
    HotSourcesKeys.hotSourcesInstall := HotSourcesKeys.hotSourcesInstall.in(Global).value,
    // Repeat definition so that sbt shows autocopmletion for these settings
    HotSourcesKeys.hotSourcesExportJarClassifiers := HotSourcesKeys.hotSourcesExportJarClassifiers.in(Global).value,
    HotSourcesKeys.hotSourcesAggregateSourceDependencies := HotSourcesKeys.hotSourcesAggregateSourceDependencies
      .in(Global)
      .value
  )

  def configSettings: Seq[Def.Setting[_]] = List(
    HotSourcesKeys.hotSourcesTargetName := hotSourcesTargetName.value,
    HotSourcesKeys.hotSourcesGenerate := hotSourcesGenerate.value,
    HotSourcesKeys.hotSourcesInternalClasspath := hotSourcesInternalDependencyClasspath.value
  )

  val projectSettings: Seq[Def.Setting[_]] =
    sbt.inConfig(Compile)(configSettings) ++
      sbt.inConfig(Test)(configSettings) ++
      sbt.inConfig(IntegrationTest)(configSettings) ++
      List(
        HotSourcesKeys.hotSourcesTargetDir := hotSourcesTargetDir.value,
        HotSourcesKeys.hotSourcesConfigDir := Def.settingDyn {
          val ref = Keys.thisProjectRef.value
          val rootBuild = sbt.BuildRef(Keys.loadedBuild.value.root)
          Def.setting {
            (HotSourcesKeys.hotSourcesConfigDir in Global).?.value.getOrElse {
              if (HotSourcesKeys.hotSourcesAggregateSourceDependencies.in(Global).value) {
                (rootBuild / Keys.baseDirectory).value / ".hotSources"
              } else {
                (Keys.baseDirectory in ref in ThisBuild).value / ".hotSources"
              }
            }
          }
        }.value
      )

  /** Replace the implementation of discovered sbt plugins so that we don't run it when we `bloopGenerate` or
    * `bloopInstall`. This is important because when there are sbt plugins in the build they trigger the compilation of
    * all the modules. We do no-op when there is indeed an sbt plugin in the build.
    */
  def discoveredSbtPluginsSettings: Seq[Def.Setting[_]] = List(
    Keys.discoveredSbtPlugins := Def.taskDyn {
      val roots = Keys.executionRoots.value
      if (!Keys.sbtPlugin.value) inlinedTask(PluginDiscovery.emptyDiscoveredNames)
      else {
        if (roots.exists(scoped => scoped.key == HotSourcesKeys.hotSourcesInstall.key)) {
          inlinedTask(PluginDiscovery.emptyDiscoveredNames)
        } else {
          Def.task(PluginDiscovery.discoverSourceAll(Keys.compile.value))
        }
      }
    }.value
  )

  // Mostly used by the sbt-scripted tests
  def unsafeParseConfig(jsonConfig: Path): Config.File = {
    hotsources.config.read(jsonConfig) match {
      case Right(config) => config
      case Left(t) =>
        System.err.println(s"Unexpected error when parsing $jsonConfig: ${t.getMessage}, skipping!")
        throw t
    }
  }

  def safeParseConfig(jsonConfig: Path, logger: Logger): Option[Config.File] = {
    hotsources.config.read(jsonConfig) match {
      case Right(config) => Some(config)
      case Left(t) =>
        logger.error(s"Unexpected error when parsing $jsonConfig: ${t.getMessage}, skipping!")
        logger.trace(t)
        None
    }
  }

  lazy val hotSourcesTargetName: Def.Initialize[String] = Def.setting {
    val logger = Keys.sLog.value
    val project = Keys.thisProject.value
    val configuration = Keys.configuration.value
    projectNameFromString(project.id, configuration, logger)
  }
  case class GeneratedProject(
      outPath: Path,
      project: Config.Project,
      fromSbtUniverseId: String
  )

  private[hotsources] val targetNamesToPreviousConfigs =
    new ConcurrentHashMap[String, GeneratedProject]()
  private[hotsources] val targetNamesToConfigs =
    new ConcurrentHashMap[String, GeneratedProject]()

  def hotSourcesGenerate: Def.Initialize[Task[Option[File]]] = Def.taskDyn {
    val logger = Keys.streams.value.log
    val project = Keys.thisProject.value
    val scoped = Keys.resolvedScoped.value
    val configuration = Keys.configuration.value
    val isMetaBuild = HotSourcesKeys.hotSourcesIsMetaBuild.value
    val hasConfigSettings = productDirectoriesUndeprecatedKey.?.value.isDefined
    val projectName = projectNameFromString(project.id, configuration, logger)
    val currentSbtUniverse = HotSourcesKeys.hotSourcesGlobalUniqueId.value
    val tags = configuration match {
      case IntegrationTest => List(Tag.IntegrationTest)
      case Test            => List(Tag.Test)
      case _               => List(Tag.Library)
    }

    lazy val generated = Option(targetNamesToConfigs.get(projectName))
    if (isMetaBuild && configuration == Test) inlinedTask[Option[File]](None)
    else if (!hasConfigSettings) inlinedTask[Option[File]](None)
    else if (generated.isDefined && generated.get.fromSbtUniverseId == currentSbtUniverse) {
      Def.task {
        // Force source generators on this task manually
        val _ignored = Keys.managedSources.value

        // Force classpath to force side-effects downstream to fully simulate `hotSourcesGenerate`
        val _ = emulateDependencyClasspath.value
        generated.map(_.outPath.toFile)
      }
    } else {
      Def.task {
        val baseDirectory = Keys.baseDirectory.value.toPath.toAbsolutePath
        val buildBaseDirectory = Keys.baseDirectory.in(ThisBuild).value.getAbsoluteFile
        val rootBaseDirectory = new File(Keys.loadedBuild.value.root)

        val bloopConfigDir = HotSourcesKeys.hotSourcesConfigDir.value
        sbt.IO.createDirectory(bloopConfigDir)
        val outFile = bloopConfigDir / s"$projectName.json"
        val outFilePath = outFile.toPath

        // Important that it's lazy, we want to avoid the price of reading config file if possible
        lazy val previousConfigFile = {
          if (!Files.exists(outFilePath)) None
          else {
            safeParseConfig(outFilePath, logger).map(_.project)
          }
        }

        val classpath = emulateDependencyClasspath.value.map(_.toPath.toAbsolutePath).toList

        val binaryModules = configModules(Keys.update.value)
        val sourceModules = {
          val sourceModulesFromSbt = updateClassifiers.value
          if (sourceModulesFromSbt.nonEmpty) sourceModulesFromSbt
          else {
            val previousAllModules =
              previousConfigFile.flatMap(_.resolution.map(_.modules)).toList.flatten
            previousAllModules.filter(module => module.artifacts.exists(_.classifier == Some("sources")))
          }
        }

        val allModules = mergeModules(binaryModules, sourceModules)
        val resolution = {
          val modules = onlyCompilationModules(allModules, classpath).toList
          if (modules.isEmpty) None else Some(Config.Resolution(modules))
        }
        val config = {
          val project =
            Config.Project(
              name = projectName,
              directory = baseDirectory,
              workspaceDir = Option(buildBaseDirectory.toPath),
              resolution = resolution
            )
          Config.File(Config.File.LatestVersion, project)
        }

        writeConfigAtomically(config, outFile.toPath)

        // Only shorten path for configuration files written to the the root build
        val allInRoot = HotSourcesKeys.hotSourcesAggregateSourceDependencies.in(Global).value
        val userFriendlyConfigPath = {
          if (allInRoot || buildBaseDirectory == rootBaseDirectory)
            outFile.relativeTo(rootBaseDirectory).getOrElse(outFile)
          else outFile
        }

        targetNamesToConfigs
          .put(projectName, GeneratedProject(outFile.toPath, config.project, currentSbtUniverse))

        logger.debug(s"Hot sources wrote the configuration of project '$projectName' to '$outFile'")
        logger.success(s"Generated $userFriendlyConfigPath")
        Some(outFile)
      }
    }
  }

  /** Write configuration to target file atomically. We achieve atomic semantics by writing to a temporary file first
    * and then moving.
    *
    * An atomic write is required to avoid clients of this target file to see an empty file and attempt to parse it (and
    * fail). This empty file is caused by, for example, `Files.write` whose semantics truncates the size of the file to
    * zero if it exists. If, for some reason, the clients access the file while write is ongoing, then they will see it
    * empty and fail.
    */
  private def writeConfigAtomically(config: Config.File, target: Path): Unit = {
    // Create unique tmp path so that move is always atomic (avoids copies across file systems!)
    val tmpPath = target.resolve(target.getParent.resolve(target.getFileName + ".tmp"))
    hotsources.config.write(config, tmpPath)
    Files.move(tmpPath, target, StandardCopyOption.REPLACE_EXISTING)
    ()
  }

  private final val allJson = sbt.GlobFilter("*.json")
  private final val removeStaleProjects = {
    allConfigDirs: Set[File] =>
      { (state: State, generatedFiles: Set[Option[File]]) =>
        val logger = state.globalLogging.full
        val allConfigs =
          allConfigDirs.flatMap(configDir => sbt.PathFinder(configDir).*(allJson).get)
        allConfigs.diff(generatedFiles.flatMap(_.toList)).foreach { configFile =>
          if (configFile.getName() == "bloop.settings.json") ()
          else {
            sbt.IO.delete(configFile)
            logger.warn(s"Removed stale $configFile")
          }
        }
        state
      }
  }

  def hotSourcesGlobalUniqueIdTask: Def.Initialize[String] = Def.setting {
    // Create a new instance of any class, gets its hash code and stringify it to get a global id
    (new scala.util.Random().hashCode()).toString
  }

  def hotSourcesInstall: Def.Initialize[Task[Unit]] = Def.taskDyn {
    val filter = sbt.ScopeFilter(
      sbt.inAnyProject,
      sbt.inAnyConfiguration,
      sbt.inTasks(HotSourcesKeys.hotSourcesGenerate)
    )

    // Clear the global map of available names and add the ones detected now
    val loadedBuild = Keys.loadedBuild.value
    allProjectNames.clear()
    projectNameReplacements.clear()
    allProjectNames.++=(loadedBuild.allProjectRefs.map(_._1.project))

    val allConfigDirs =
      HotSourcesKeys.hotSourcesConfigDir.?.all(sbt.ScopeFilter(sbt.inAnyProject))
        .map(_.flatMap(_.toList).toSet)
        .value
    val removeProjects = removeStaleProjects(allConfigDirs)
    HotSourcesKeys.hotSourcesGenerate
      .all(filter)
      .map(_.toSet)
      // Smart trick to modify state once a task has completed (who said tasks cannot alter state?)
      .apply((t: Task[Set[Option[File]]]) => sbt.SessionVar.transform(t, removeProjects))
      .map(_ => ())
  }

  lazy val hotSourcesConfigDir: Def.Initialize[Option[File]] = Def.setting { None }
  import sbt.Classpaths

  private def wrapWithInitialize[T](value: Task[T]): Def.Initialize[Task[T]] = Def.value(value)
  private def inlinedTask[T](value: T): Def.Initialize[Task[T]] = Def.toITask(Def.value(value))

  /** Emulates `dependencyClasspath` without triggering compilation of dependent projects.
    *
    * Why do we do this instead of a simple `productDirectories ++ libraryDependencies`? We want the classpath to have
    * the correct topological order of the project dependencies.
    */
  final def hotSourcesInternalDependencyClasspath: Def.Initialize[Task[Seq[(File, File)]]] = {
    Def.taskDyn {
      val currentProject = Keys.thisProjectRef.value
      val data = Keys.settingsData.value
      val deps = Keys.buildDependencies.value
      val self = Keys.configuration.value

      Keys.classpathConfiguration.?.value match {
        case Some(conf) =>
          import scala.collection.JavaConverters._
          val visited = Classpaths.interSort(currentProject, conf, data, deps)
          val productDirs = (new java.util.LinkedHashSet[Task[Seq[File]]]).asScala
          val hotSourcesProductDirs = (new java.util.LinkedHashSet[Task[Seq[File]]]).asScala
          for ((dep, c) <- visited) {
            if ((dep != currentProject) || (conf.name != c && self.name != c)) {
              val classpathKey = productDirectoriesUndeprecatedKey in (dep, sbt.ConfigKey(c))
              if (classpathKey.get(data).isEmpty) ()
              else {
                productDirs += classpathKey.get(data).getOrElse(sbt.std.TaskExtra.constant(Nil))
                val hotSourcesKey = HotSourcesKeys.hotSourcesProductDirectories in (dep, sbt.ConfigKey(c))
                hotSourcesProductDirs += hotSourcesKey.get(data).getOrElse(sbt.std.TaskExtra.constant(Nil))
              }
            }
          }

          wrapWithInitialize(
            productDirs.toList.join.map(_.flatten.distinct).flatMap { a =>
              hotSourcesProductDirs.toList.join.map(_.flatten.distinct).map { b =>
                a.zip(b)
              }
            }
          )
        case None => inlinedTask(Nil)
      }

    }
  }

  def emulateDependencyClasspath: Def.Initialize[Task[Seq[File]]] = Def.task {
    val internalClasspath = HotSourcesKeys.hotSourcesInternalClasspath.value.map(_._2)
    val externalClasspath = Keys.externalDependencyClasspath.value.map(_.data)
    internalClasspath ++ externalClasspath
  }

  def hotSourcesTargetDir: Def.Initialize[File] = Def.setting {
    val project = Keys.thisProject.value
    val hotSourcesConfigDir = HotSourcesKeys.hotSourcesConfigDir.value
    Defaults.makeCrossTarget(
      hotSourcesConfigDir / project.id,
      Keys.scalaBinaryVersion.value,
      (Keys.pluginCrossBuild / Keys.sbtBinaryVersion).value,
      Keys.sbtPlugin.value,
      Keys.crossPaths.value
    )
  }

  /** Keep a map of all the project names registered in this build load.
    *
    * This map is populated in [[hotSourcesInstall]] before [[hotSourcesGenerate]] is run, which means that by the time
    * [[projectNameFromString]] runs this map will already contain an updated list of all the projects in the build.
    *
    * This information is paramount so that we don't generate a project with the same name of a valid user-facing
    * project. For example, if a user defines two projects named `foo` and `foo-test`, we need to make sure that the
    * test configuration for `foo`, mapped to `foo-test` does not collide with the compile configuration of `foo-test`.
    */
  private final val allProjectNames = new scala.collection.mutable.HashSet[String]()

  /** Cache any replacement that has happened to a project name. */
  private final val projectNameReplacements =
    new java.util.concurrent.ConcurrentHashMap[String, String]()

  def projectNameFromString(name: String, configuration: Configuration, logger: Logger): String = {
    if (configuration == Compile) name
    else {
      val supposedName = s"$name-${configuration.name}"
      // Let's check if the default name (with no append of anything) is not used by another project
      if (!allProjectNames.contains(supposedName)) supposedName
      else {
        val existingReplacement = projectNameReplacements.get(supposedName)
        if (existingReplacement != null) existingReplacement
        else {
          // Use `+` instead of `-` as separator and report the user about the change
          val newUnambiguousName = s"$name+${configuration.name}"
          projectNameReplacements.computeIfAbsent(
            supposedName,
            new java.util.function.Function[String, String] {
              override def apply(supposedName: String): String = {
                logger.warn(
                  s"[HotSources] - Derived target name '${supposedName}' already exists in the build, changing to ${newUnambiguousName}"
                )
                newUnambiguousName
              }
            }
          )
        }
      }
    }
  }

  def checksumFor(path: Path, algorithm: String): Option[Config.Checksum] = {
    val presumedChecksumFilename = s"${path.getFileName}.$algorithm"
    val presumedChecksum = path.getParent.resolve(presumedChecksumFilename)
    if (!Files.isRegularFile(presumedChecksum)) None
    else {
      Try(new String(Files.readAllBytes(presumedChecksum), StandardCharsets.UTF_8)) match {
        case Success(checksum) => Some(Config.Checksum(algorithm, checksum))
        case Failure(_)        => None
      }
    }
  }

  def configModules(report: sbt.UpdateReport): Seq[Config.Module] = {
    val moduleReports = for {
      configuration <- report.configurations
      module <- configuration.modules
    } yield module

    moduleReports.map { mreport =>
      val artifacts = mreport.artifacts.toList.map { case (a, f) =>
        val path = f.toPath
        val artifact = toHotSourcesArtifact(a, f)
        artifact.checksum match {
          case Some(_) => artifact
          case None    =>
            // If sbt hasn't filled in the checksums field, let's try to do it ourselves
            val checksum = checksumFor(path, "sha1").orElse(checksumFor(path, "md5"))
            artifact.copy(checksum = checksum)
        }
      }

      val m = mreport.module
      Config.Module(m.organization, m.name, m.revision, m.configurations, artifacts)
    }
  }

  def mergeModules(ms0: Seq[Config.Module], ms1: Seq[Config.Module]): Seq[Config.Module] = {
    ms0.map { m0 =>
      ms1.find(m => m0.organization == m.organization && m0.name == m.name && m0.version == m.version) match {
        case Some(m1) => m0.copy(artifacts = (m0.artifacts ++ m1.artifacts).distinct)
        case None     => m0
      }
    }.distinct
  }

  def onlyCompilationModules(ms: Seq[Config.Module], classpath: List[Path]): Seq[Config.Module] = {
    val classpathFiles = classpath.filter(p => Files.exists(p) && !Files.isDirectory(p))
    if (classpathFiles.isEmpty) Nil
    else {
      ms.filter { m =>
        // The artifacts that have no classifier are the normal binary jars we're interested in
        m.artifacts.filter(a => a.classifier.isEmpty).exists { a =>
          classpathFiles.exists(p => Files.isSameFile(a.path, p))
        }
      }
    }
  }

  lazy val updateClassifiers: Def.Initialize[Task[Seq[Config.Module]]] = Def.taskDyn {
    val runUpdateClassifiers = HotSourcesKeys.hotSourcesExportJarClassifiers.value.nonEmpty
    if (!runUpdateClassifiers) Def.task(Seq.empty)
    else if (HotSourcesKeys.hotSourcesIsMetaBuild.value)
      Def.task {
        configModules(Keys.updateSbtClassifiers.value) ++
          configModules(Keys.updateClassifiers.value)
      }
    else Def.task(configModules(Keys.updateClassifiers.value))
  }
}

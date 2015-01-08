import sbt._
import Keys._

object Dependencies {
  val sbtVersion = "0.13.6"
  val sbtLibraryVersion = "0.13.6" // for sbtIO on scala 2.11

  val sbtPluginVersion = "0.13"
  val sbtPluginScalaVersion = "2.11.4"
  val scalaVersion = "2.11.4"
  val luceneVersion = "4.2.1"

  val templateCacheVersion = "1.0-6830c15252733edf977c869af798d113ad5ac80d"
  val sbtRcVersion = "1.0-951c90820f296b4eda7eb9759e8d80f148065cd6"

  val playVersion = "2.3.3"
  val akkaVersion = "2.3.4"
  val slickVersion = "2.1.0"
  val echoPluginVersion = "0.1.7"
  val activatorAnalyticsVersion = "0.1.6"
  val aspectJVersion = "1.7.3"

  val activatorCommon      = "com.typesafe.activator" % "activator-common" % templateCacheVersion
  val templateCache        = "com.typesafe.activator" % "activator-templates-cache" % templateCacheVersion

  // 2.10 sbt
  val sbtIo210             = "org.scala-sbt" % "io" % sbtVersion
  // launcher interface is pure Java, no scala versioning
  val sbtLauncherInterface = "org.scala-sbt" % "launcher-interface" % sbtVersion

  // 2.11 sbt
  val sbtIo                = "org.scala-sbt" %% "io" % sbtLibraryVersion
  val sbtCompletion        = "org.scala-sbt" %% "completion" % sbtLibraryVersion

  // sbtrc projects
  val sbtrcClient          = "com.typesafe.sbtrc" % "client-2-11" % sbtRcVersion
  val sbtrcIntegration     = "com.typesafe.sbtrc" % "integration-tests" % sbtRcVersion

  val akkaActor            = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaSlf4j            = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  val akkaTestkit          = "com.typesafe.akka" %% "akka-testkit"% akkaVersion

  val commonsIo            = "commons-io" % "commons-io" % "2.0.1"

  val mimeUtil             = "eu.medsea.mimeutil" % "mime-util" % "2.1.1" exclude("org.slf4j", "slf4j-log4j12") exclude("org.slf4j", "slf4j-api") exclude("log4j", "log4j")

  val junitInterface       = "com.novocode" % "junit-interface" % "0.7"
  val specs2               = "org.specs2" % "specs2_2.11" % "2.3.11"

  // SBT 0.13 required plugins
  val playSbt13Plugin        =  Defaults.sbtPluginExtra("com.typesafe.play" % "sbt-plugin" % playVersion, "0.13", "2.10")
  val eclipseSbt13Plugin     =  Defaults.sbtPluginExtra("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.2.0", "0.13", "2.10")
  val ideaSbt13Plugin        =  Defaults.sbtPluginExtra("com.github.mpeltonen" % "sbt-idea" % "1.5.2", "0.13", "2.10")
  val echoSbt13Plugin        =  Defaults.sbtPluginExtra("com.typesafe.sbt" % "sbt-echo-play" % echoPluginVersion, "0.13", "2.10")
  val echoPlaySbt13Plugin    =  Defaults.sbtPluginExtra("com.typesafe.sbt" % "sbt-echo" % echoPluginVersion, "0.13", "2.10")

  // Embedded databases / index
  val lucene = "org.apache.lucene" % "lucene-core" % luceneVersion
  val luceneAnalyzerCommon = "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion
  val luceneQueryParser = "org.apache.lucene" % "lucene-queryparser" % luceneVersion

  // WebJars for the Activator UI
  val requirejs        = "org.webjars" % "requirejs" % "2.1.11"
  val jquery           = "org.webjars" % "jquery" % "2.0.3"
  val knockout         = "org.webjars" % "knockout" % "3.0.0"
  val ace              = "org.webjars" % "ace" % "1.1.7-1"
  val keymage          = "org.webjars" % "keymage" % "1.0.1"

  // Analyzers used by Inspect
  val activatorAnalytics = "com.typesafe.activator"  %% "analytics" % activatorAnalyticsVersion

  // Mini DSL
  // DSL for adding remote deps like local deps.
  implicit def p2remote(p: Project): RemoteDepHelper = new RemoteDepHelper(p)
  final class RemoteDepHelper(p: Project) {
    def dependsOnRemote(ms: ModuleID*): Project = p.settings(libraryDependencies ++= ms)
  }
  // DSL for adding source dependencies ot projects.
  def dependsOnSource(dir: String): Seq[Setting[_]] = {
    import Keys._
    Seq(unmanagedSourceDirectories in Compile <<= (unmanagedSourceDirectories in Compile, baseDirectory) { (srcDirs, base) => (base / dir / "src/main/scala") +: srcDirs },
        unmanagedSourceDirectories in Test <<= (unmanagedSourceDirectories in Test, baseDirectory) { (srcDirs, base) => (base / dir / "src/test/scala") +: srcDirs })
  }
  implicit def p2source(p: Project): SourceDepHelper = new SourceDepHelper(p)
  final class SourceDepHelper(p: Project) {
    def dependsOnSource(dir: String): Project =
      p.settings(Dependencies.dependsOnSource(dir):_*)
  }

  // compile classpath and classes directory, with provided/optional or scala dependencies
  // specifically for projects that need remote-probe dependencies
  val requiredClasspath = TaskKey[Classpath]("required-classpath")

  def requiredJars(deps: ProjectReference*): Setting[_] = {
    import xsbti.ArtifactInfo._
    import Project.Initialize
    val dependentProjectClassPaths: Seq[Initialize[Task[Seq[File]]]] =
      (deps map { proj =>
        (classDirectory in Compile in proj) map { dir => Seq(dir) }
      })
    val ivyDeps: Initialize[Task[Seq[File]]] =  update map { report =>
      val jars = report.matching(configurationFilter(name = "compile") -- moduleFilter(organization = ScalaOrganization, name = ScalaLibraryID))
      jars
    }
    val localClasses: Initialize[Task[Seq[File]]] = (classDirectory in Compile) map { dir =>
      Seq(dir)
    }
    // JOin everyone
    def joinCp(inits: Seq[Initialize[Task[Seq[File]]]]): Initialize[Task[Seq[File]]] =
      inits reduce { (lhs, rhs) =>
        (lhs zip rhs).flatMap { case (l,r) =>
          l.flatMap[Seq[File]] { files =>
            r.map[Seq[File]] { files2 =>
              files ++ files2
            }
          }
        }
      }
    requiredClasspath <<= joinCp(dependentProjectClassPaths ++ Seq(ivyDeps, localClasses)) map {
      _.classpath
    }
  }

  // *** ECHO DEPENDENCIES ***
  // TODO : put common version numbers in one place
  val akkaSlf4j22 = "com.typesafe.akka"   %% "akka-slf4j"   % "2.2.4"
  val akkaSlf4j23 = "com.typesafe.akka"   %% "akka-slf4j"   % "2.3.8"
  val config      = "com.typesafe"        % "config"        % "1.2.1"
  val play22      = "com.typesafe.play"   %% "play"         % "2.2.6"
  val play23      = "com.typesafe.play"   %% "play"         % "2.3.7"
  val play23ws    = "com.typesafe.play"   %% "play-ws"      % "2.3.7"
  val protobuf24  = "com.google.protobuf" % "protobuf-java" % "2.4.1"
  val protobuf25  = "com.google.protobuf" % "protobuf-java" % "2.5.0"
  val sigar       = "org.fusesource"      % "sigar"         % "1.6.4"
  val slf4j       = "org.slf4j"           % "slf4j-api"     % "1.7.5"

  val akkaTestKit22 = "com.typesafe.akka" %% "akka-testkit" % "2.2.4"   % "test"
  val akkaTestKit23 = "com.typesafe.akka" %% "akka-testkit" % "2.3.8"   % "test"
  val junit       = "junit"               % "junit"         % "4.5"     % "test"
  val logback     = "ch.qos.logback"      % "logback-classic" % "1.0.13" % "test"
  val playTest23  = "com.typesafe.play"   %% "play-test"    % "2.3.7"   % "test"
  val playTest22  = "com.typesafe.play"   %% "play-test"    % "2.2.6"   % "test"
  val scalaTest   = "org.scalatest"       %% "scalatest"    % "2.2.1"   % "test"

  def traceAkka(version: String, crossVersion: CrossVersion) = Seq(
    "com.typesafe.akka" % "akka-actor"  % version cross crossVersion,
    "com.typesafe.akka" % "akka-remote" % version cross crossVersion,
    "com.typesafe.akka" % "akka-slf4j"  % version cross crossVersion,
    sigar
  )

  val traceAkkaExcludes = {
    <dependencies>
      <exclude module="slf4j-simple"/>
    </dependencies>
  }
  // *** END ECHO DEPENDENCIES ***

  // *** SBT-ECHO DEPENDENCIES ***
  // TODO : put common version numbers in one place

  val aspectjTools = "org.aspectj" % "aspectjtools" % "1.8.4"

  val sbtBackgroundRun = Defaults.sbtPluginExtra("com.typesafe.sbtrc" % "ui-interface-0-13" % sbtRcVersion, "0.13", "2.10")

  def playPlugin: Seq[Setting[_]] = Seq(
    resolvers += Classpaths.typesafeSnapshots,
    resolvers += "Typesafe Maven Snapshots" at "http://repo.typesafe.com/typesafe/snapshots/",
    resolvers += "Typesafe Maven Releases" at "http://repo.typesafe.com/typesafe/releases/",
    libraryDependencies <+= (sbt.Keys.sbtVersion in sbtPlugin, scalaBinaryVersion in update) { (sbtV, scalaV) =>
      val dependency = sbtV match {
        case "0.12" => "play" % "sbt-plugin" % "2.1.5" exclude("com.github.scala-incubator.io", "scala-io-core_2.9.1") exclude("com.github.scala-incubator.io", "scala-io-file_2.9.1")
        case "0.13" => "com.typesafe.play" % "sbt-plugin" % playVersion
        case _ => sys.error("Unsupported sbt version: " + sbtV)
      }
      Defaults.sbtPluginExtra(dependency, sbtV, scalaV)
    }
  )
  // *** END SBT-ECHO DEPENDENCIES ***


}

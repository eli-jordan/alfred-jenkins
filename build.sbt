import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.stage

import scala.sys.process.{Process => SProcess}

ThisBuild / scalaVersion := "2.13.6"
ThisBuild / organization := "com.elijordan"

val circeVersion = "0.13.0"

lazy val `alfred-jenkins` = (project in file("."))
  .settings(
    ghreleaseRepoOrg := "eli-jordan",
    ghreleaseRepoName := "alfred-jenkins",
    ghreleaseAssets := Seq(
      (workflow / alfredWorkflowPackage).value
    ),
    ghreleaseMediaTypesMap := { _ =>
      "application/zip"
    },
    ghreleaseNotes := { tagName =>
      val version = tagName.stripPrefix("v")
      IO.read(baseDirectory.value / "notes" / s"$version.md")
    },
    addCommandAlias("fmt", "workflow/scalafmtAll; scalafmtSbt"),
    addCommandAlias("fmt-check", "workflow/scalafmtCheckAll; scalafmtSbtCheck"),
    addCommandAlias("run-tests", "workflow/clean; coverage; workflow/test; workflow/coverageReport"),
    addCommandAlias("run-package", "workflow/clean; workflow/alfredWorkflowPackage")
  )

/**
  * Graal Native Image Notes
  * ---
  *
  * > Config was boot strapped from https://github.com/inner-product/http4s-native-image
  *
  * 1. "pt.davidafsilva.apple" % "jkeychain" uses JNI to interact with the osx keychain. This requires some special config
  *     to work with native-image.
  *
  *     - The classes that are used need to be enumerated in META-INF/native-image/com.elijordan/alfred-jenkins/jni-config.json
  *       This config we generated using the graal native image agent.
  *
  *     - The library includes the osxkeychain.so library, that is invoked using JNI. This library is loaded as a resource, so
  *       needs to be declared in META-INF/native-image/com.elijordan/alfred-jenkins/resource-config.json
  *
  * 2. scala.runtime.Statics$VM needs to be initialised at native-image build time, to avoid errors. See this issue for details
  *     - https://github.com/scala/bug/issues/11634
  *     - https://github.com/oracle/graal/issues/2019
  *
  *    However, when just including this class in --initialize-at-build-time native-image errors out indicating that several classes
  *    were initialized at build time, but should not have. So, we just initialize the whole scala.* package at build time.
  *
  * 3. Any classes that are interacted with using reflection need to be enumerated. In this application its just logback and
  *    slf4j see META-INF/native-image/com.elijordan/alfred-jenkins/reflect-config.json
  *
  * If the classes that are initialized at build time needs to be scoped down, the minimal set is.
  *
  *  {{{
  *     val initAtBuild = List(
  *         "scala.Predef$",
  *         "scala.reflect.ClassTag$",
  *         "scala.math.BigInt$",
  *         "scala.concurrent.duration.Duration$",
  *         "scala.package$",
  *         "scala.collection.immutable.IndexedSeq$",
  *         "scala.collection.Iterable$",
  *         "scala.reflect.ManifestFactory$",
  *         "scala.reflect.Manifest$",
  *         "scala.collection.immutable.LazyList$",
  *         "scala.collection.immutable.Iterable$",
  *         "scala.collection.immutable.List$",
  *         "scala.collection.immutable.Seq$",
  *         "scala.collection.immutable.Vector$"
  *      )
  *  }}}
  */
lazy val `workflow` = (project in file("workflow"))
  .settings(
    name := "alfred-jenkins",
    sonarExpectSonarQubeCommunityPlugin := false,
    graalVMNativeImageOptions := Seq(
      "--verbose",
      "-H:+TraceClassInitialization",
      "-H:+ReportExceptionStackTraces",
      "--no-fallback",
      "--allow-incomplete-classpath",
      "--initialize-at-build-time=scala",
      "--enable-http",
      "--enable-https"
    ),
    mainClass in Compile := Some("alfred.jenkins.AlfredJenkins"),
    alfredWorkflowName := "Jenkins",
    alfredWorkflowDir := baseDirectory.value.getParentFile / "alfred",
    alfredWorkflowExtraFiles := Seq(
      (packageBin in GraalVMNativeImage).value
    ),
    alfredWorkflowVariables := Map(
      "ALFRED_JENKINS_COMMAND" -> "./alfred-jenkins",
      "keyword_prefix"         -> ""
    ),
    alfredWorkflowBundleId := "com.elijordan.alfred.jenkins",
    libraryDependencies ++= Seq(
      "io.circe"             %% "circe-core"           % circeVersion,
      "io.circe"             %% "circe-generic"        % circeVersion,
      "io.circe"             %% "circe-generic-extras" % circeVersion,
      "pt.davidafsilva.apple" % "jkeychain"            % "1.0.0",
      "org.typelevel"        %% "cats-effect"          % "2.2.0-RC1",
      "org.http4s"           %% "http4s-blaze-client"  % "0.21.6",
      "org.http4s"           %% "http4s-circe"         % "0.21.6",
      "org.http4s"           %% "http4s-dsl"           % "0.21.6",
      "com.monovore"         %% "decline"              % "1.0.0",
      "io.chrisdavenport"    %% "log4cats-slf4j"       % "1.1.1",
      "ch.qos.logback"        % "logback-classic"      % "1.2.3",
      "org.scalatest"        %% "scalatest"            % "3.2.0" % Test
    )
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(GraalVMNativeImagePlugin)
  .enablePlugins(AlfredWorkflowPlugin)

/**
  * Defines a task that will install the workflow in a development setup.
  *
  * It -
  * 1. Stages the workflow application using `universal:stage`.
  *    Note: Graal native-image is not used, since its very slow to build.
  * 2. Stages the workflow package using `alfredWorkflowStage`
  * 3. Symlinks the staged workflow package into the Alfred workflow data directory.
  * 4. Symlinks info.plist in the staged directory to the one in alfredWorkflowDir.
  *    This means edits made to the workflow using the alfred UI will be reflected in the source
  *    controlled info.plist.
  * 5. Symlinks the `alfred-jenkins` command in staged package to the startup script generated by `universal:stage`
  *
  * Local dev can be setup by running local-dev/link
  */
lazy val link = taskKey[Unit]("Links the workflow into the Alfred workflows directory")
lazy val `local-dev` = (project in file("local-dev"))
  .settings(
    alfredWorkflowName := "Jenkins (Dev)",
    alfredWorkflowDir := baseDirectory.value.getParentFile / "alfred",
    alfredWorkflowExtraFiles := Seq.empty,
    alfredWorkflowBundleId := "com.elijordan.alfred.jenkins.dev",
    link := {
      val workflowStagingDir = Def
        .sequential(
          clean,
          `workflow` / Universal / stage,
          alfredWorkflowStage
        )
        .value

      val universalLauncher = (`workflow` / target).value / "universal" / "stage" / "bin" / "alfred-jenkins"

      val workflowsDir = sys
        .props("user.home") + "/Library/Application Support/Alfred/Alfred.alfredpreferences/workflows/"
      val linkDir = workflowsDir + "user.workflow.jenkins"
      val command =
        SProcess(Seq("rm", "-rf", linkDir)) #&&
          SProcess(Seq("ln", "-s", workflowStagingDir.toString, linkDir)) #&&
          SProcess(Seq("rm", "-f", (workflowStagingDir / "info.plist").toString)) #&&
          SProcess(
            Seq(
              "ln",
              "-s",
              (alfredWorkflowDir.value / "info.plist").toString,
              (workflowStagingDir / "info.plist").toString)) #&&
          SProcess(Seq("ln", "-s", universalLauncher.toString, (workflowStagingDir / "alfred-jenkins").toString))
      val code = command.!
      if (code != 0) sys.error(s"Failed to link staging dir into alfred workflows directory.")
    }
  )
  .enablePlugins(AlfredWorkflowPlugin)

import sbt._
import Keys._
import com.dd.plist.{NSArray, NSDictionary, NSString, PropertyListParser}

import scala.sys.process.{Process => SProcess}

/**
  * An sbt plugin for packaging alfred workflows
  */
object AlfredWorkflowPlugin extends AutoPlugin {

  object autoImport {

    /**
      * This is the name of the workflow. The value will be injected into the `info.plist` file
      * and will also be the name of the generated .alfredworkflow artifact.
      */
    val alfredWorkflowName = settingKey[String]("The name of the workflow")

    /**
      * The version of the workflow. This will be injected into the `info.plist` file.
      *
      * Defaults to the value of the `version` setting
      */
    val alfredWorkflowVersion = settingKey[String]("The workflow version number")

    /**
      * The workflow bundle id. This will be injected into the `info.plist` file.
      *
      * Required.
      */
    val alfredWorkflowBundleId = settingKey[String]("The bundle id for the workflow")

    /**
      * The directory that holds all static files that should be copied into the published workflow.
      *
      * - There MUST be an `info.plist` file describing the workflow in this directory.
      * - If there is a readme.txt file, it will be copied into the workflow readme.
      *
      * Defaults to baseDirectory.value / "alfred"
      */
    val alfredWorkflowDir = settingKey[File]("The directory where workflow files are stored")

    /**
      * Workflow variables that should be injected into the `info.plist` prior to packaging.
      */
    val alfredWorkflowVariables = settingKey[Map[String, String]]("Workflow variables to inject prior to publishing")

    /**
      * Additional files that are not in [[alfredWorkflowDir]] that should be packaged with the workflow.
      * This is generally used to copy build artifacts (such as an executable) into the workflow.
      */
    val alfredWorkflowExtraFiles =
      taskKey[Seq[File]]("The list directories or files to include in the published workflow")

    /**
      * Stage all files from [[alfredWorkflowDir]] and [[alfredWorkflowExtraFiles]] along with the
      * processed `info.plist` into a directory, that just needed to be zipped up.
      */
    val alfredWorkflowStage = taskKey[File]("Stage all files into the target directory")

    /**
      * Zip a staged workflow directory
      */
    val alfredWorkflowPackage = taskKey[File]("Stage and package the workflow")
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    alfredWorkflowDir := baseDirectory.value / "alfred",
    alfredWorkflowVersion := version.value,
    alfredWorkflowExtraFiles := Seq.empty,
    alfredWorkflowVariables := Map.empty,
    alfredWorkflowStage := {
      val outDir = target.value / "alfred" / "stage"
      stageWorkflow(
        name = alfredWorkflowName.value,
        version = alfredWorkflowVersion.value,
        bundleId = alfredWorkflowBundleId.value,
        dir = alfredWorkflowDir.value,
        extraFiles = alfredWorkflowExtraFiles.value,
        variables = alfredWorkflowVariables.value,
        outDir = outDir
      )
      outDir
    },
    alfredWorkflowPackage := {
      val outDir     = target.value / "alfred"
      val stagingDir = alfredWorkflowStage.value
      val name       = alfredWorkflowName.value
      packageWorkflow(stagingDir, name, outDir)
    }
  )

  def stageWorkflow(
      name: String,
      version: String,
      bundleId: String,
      dir: File,
      extraFiles: Seq[File],
      variables: Map[String, String],
      outDir: File
  ): Unit = {

    processPlist(
      name = name,
      plist = dir / "info.plist",
      readme = dir / "readme.txt",
      bundleId = bundleId,
      version = version,
      variables = variables,
      outDir = outDir
    )

    val files = dir.listFiles().toList.filter(_.getName != "info.plist")

    (files ++ extraFiles).foreach { file =>
      println(s"Copying file $file to $outDir")
      if (file.isDirectory) IO.copyDirectory(file, outDir / file.getName)
      else if (file.isFile) IO.copyFile(file, outDir / file.getName)
    }
  }

  def packageWorkflow(stagingDir: File, name: String, outDir: File): File = {
    val workflowPackage = outDir / s"$name.alfredworkflow"
    val code            = SProcess(Seq("zip", "-r", workflowPackage.toString, "."), Some(stagingDir)).!
    if (code != 0) sys.error("Failed to package alfred workflow")
    workflowPackage
  }

  def processPlist(
      plist: File,
      name: String,
      readme: File,
      version: String,
      bundleId: String,
      variables: Map[String, String],
      outDir: File
  ): Unit = {
    val properties = PropertyListParser.parse(plist).asInstanceOf[NSDictionary]
    properties.put("name", new NSString(name))
    properties.put("version", new NSString(version))
    properties.put("bundleid", new NSString(bundleId))

    if (readme.exists()) {
      properties.put("readme", new NSString(IO.read(readme)))
    }

    // Remove variables that are excluded from export
    val plistVars = properties.get("variables").asInstanceOf[NSDictionary]
    val nonExported = Option(properties.get("variablesdontexport")).toList
      .flatMap(_.asInstanceOf[NSArray].getArray.toList)
      .map(_.asInstanceOf[NSString])
      .map(_.toString)

    nonExported.foreach { non =>
      plistVars.remove(non)
    }

    println(s"Setting variables in info.plist: $variables")
    variables.foreach {
      case (key, value) =>
        plistVars.put(key, new NSString(value))
    }

    PropertyListParser.saveAsXML(properties, outDir / "info.plist")
  }
}

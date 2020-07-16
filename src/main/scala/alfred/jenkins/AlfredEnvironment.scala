package alfred.jenkins

import scala.util.{Failure, Success, Try}

case class AlfredEnvironment(
    alfredVersion: String,
    alfredVersionBuild: String,
    workflowBundleId: String,
    workflowCacheDir: String,
    workflowDataDir: String,
    workflowName: String,
    workflowUid: String,
    debugMode: Boolean
)

object AlfredEnvironment {
  val AlfredVersionKey          = "alfred_version"
  val AlfredVersionBuildKey     = "alfred_version_build"
  val AlfredWorkflowBundleIdKey = "alfred_workflow_bundleid"
  val AlfredWorkflowCacheKey    = "alfred_workflow_cache"
  val AlfredWorkflowDataKey     = "alfred_workflow_data"
  val AlfredWorkflowNameKey     = "alfred_workflow_name"
  val AlfredWorkflowUidKey      = "alfred_workflow_uid"
  val AlfredDebugKey            = "alfred_debug"

  def fromEnv: Try[AlfredEnvironment] = fromMap(sys.env)

  def fromMap(map: Map[String, String]): Try[AlfredEnvironment] = {
    for {
      alfredVersion      <- get(map, AlfredDebugKey)
      alfredVersionBuild <- get(map, AlfredVersionBuildKey)
      workflowBundleId   <- get(map, AlfredWorkflowBundleIdKey)
      workflowCacheDir   <- get(map, AlfredWorkflowCacheKey)
      workflowDataDir    <- get(map, AlfredWorkflowDataKey)
      workflowName       <- get(map, AlfredWorkflowNameKey)
      workflowUid        <- get(map, AlfredWorkflowUidKey)
      debugMode          <- get(map, AlfredDebugKey)
    } yield {
      AlfredEnvironment(
        alfredVersion = alfredVersion,
        alfredVersionBuild = alfredVersionBuild,
        workflowBundleId = workflowBundleId,
        workflowCacheDir = workflowCacheDir,
        workflowDataDir = workflowDataDir,
        workflowName = workflowName,
        workflowUid = workflowUid,
        debugMode = debugMode == "1"
      )
    }
  }

  private def get(map: Map[String, String], key: String): Try[String] = {
    map.get(key) match {
      case Some(value) => Success(value)
      case None        => Failure(new Exception(s"Key $key not found"))
    }
  }
}

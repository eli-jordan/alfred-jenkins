package alfred.jenkins

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

/**
  * This file contains case classes that map directly to the json data returned from the
  * jenkins API. Note however, that the fields that are present assume the filter parameter
  * defined in [[JenkinsClient#filter]]
  */
sealed abstract class JobType(val value: String) {
  def canHaveChildren: Boolean =
    this match {
      case JobType.Root            => true
      case JobType.Folder          => true
      case JobType.WorkflowJob     => false
      case JobType.FreestyleJob    => false
      case JobType.MultiBranch     => true
      case JobType.Unrecognised(_) => false
    }
}
object JobType {
  case object Root                     extends JobType("hudson.model.Hudson")
  case object Folder                   extends JobType("com.cloudbees.hudson.plugins.folder.Folder")
  case object WorkflowJob              extends JobType("org.jenkinsci.plugins.workflow.job.WorkflowJob")
  case object FreestyleJob             extends JobType("hudson.model.FreeStyleProject")
  case object MultiBranch              extends JobType("org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject")
  case class Unrecognised(str: String) extends JobType(str)

  private val Values = List(
    Root,
    Folder,
    WorkflowJob,
    FreestyleJob,
    MultiBranch
  )

  implicit val decoder: Decoder[JobType] = Decoder[String].map { string =>
    Values.find(_.value == string) match {
      case Some(v) => v
      case None    => Unrecognised(string)
    }
  }

  implicit val encoder: Encoder[JobType] = Encoder[String].contramap(_.value)
}

case class JenkinsJobsList(
    _class: JobType,
    jobs: List[JenkinsJob]
)

object JenkinsJobsList {
  implicit val decoder: Decoder[JenkinsJobsList] = deriveDecoder
  implicit val encoder: Encoder[JenkinsJobsList] = deriveEncoder
}

case class JenkinsJob(
    _class: JobType,
    fullDisplayName: String,
    displayName: String,
    url: String,
    color: Option[String],
    healthReport: List[HealthReport],
    lastBuild: Option[JenkinsBuild],
    buildable: Option[Boolean]
)

object JenkinsJob {
  implicit val decoder: Decoder[JenkinsJob] = deriveDecoder
  implicit val encoder: Encoder[JenkinsJob] = deriveEncoder
}

case class HealthReport(
    description: String,
    iconUrl: String,
    score: Int
)

object HealthReport {
  implicit val decoder: Decoder[HealthReport] = deriveDecoder
  implicit val encoder: Encoder[HealthReport] = deriveEncoder
}

case class JenkinsBuild(
    _class: JobType,
    description: Option[String],
    number: Int,
    building: Boolean,
    displayName: String,
    fullDisplayName: String,
    result: Option[String],
    timestamp: Long,
    duration: Long,
    url: String
)

object JenkinsBuild {
  implicit val decoder: Decoder[JenkinsBuild] = deriveDecoder
  implicit val encoder: Encoder[JenkinsBuild] = deriveEncoder
}

case class JenkinsBuildHistory(
    url: String,
    displayName: String,
    fullDisplayName: String,
    builds: List[JenkinsBuild]
)

object JenkinsBuildHistory {
  implicit val decoder: Decoder[JenkinsBuildHistory] = deriveDecoder
  implicit val encoder: Encoder[JenkinsBuildHistory] = deriveEncoder
}

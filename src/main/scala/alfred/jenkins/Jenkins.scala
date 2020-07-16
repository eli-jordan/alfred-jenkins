package alfred.jenkins

import cats.effect.{ContextShift, ExitCode, IO, IOApp}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{BasicCredentials, MediaType, Request, Uri}
import cats.implicits._
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

sealed abstract class JobType(val value: String) {
  def canHaveChildren: Boolean = this match {
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
    building: Boolean,
    displayName: String,
    fullDisplayName: String,
    result: Option[String],
    url: String
)

object JenkinsBuild {
  implicit val decoder: Decoder[JenkinsBuild] = deriveDecoder
  implicit val encoder: Encoder[JenkinsBuild] = deriveEncoder
}

class Jenkins(client: Client[IO], settings: Settings[AlfredJenkinsSettings], credentials: Credentials) {
  val filter =
    "jobs[displayName,fullDisplayName,url,color,buildable,healthReport[description,score,iconUrl],lastBuild[building,result,displayName,fullDisplayName,url]]"

  def fetch(path: Uri): IO[List[JenkinsJob]] = {
    for {
      credentials <- basicCredentials
      uri = path / "api" / "json" +? ("tree", filter)
      request = Request[IO]()
        .withUri(uri)
        .withHeaders(
          Accept(MediaType.application.json),
          Authorization(credentials)
        )
      jobs <- client.expect[JenkinsJobsList](request).map(_.jobs)
    } yield jobs
  }

  /**
    * Finds all jobs that are not containers of other jobs.
    */
  def scan(path: Uri)(implicit cs: ContextShift[IO]): IO[List[JenkinsJob]] = {
    for {
      list <- fetch(path)
      (branchJobs, leafJobs) = list.partition(_._class.canHaveChildren)
      jobs <- branchJobs.parFlatTraverse { job =>
        scan(Uri.unsafeFromString(job.url))
      }
    } yield
      jobs ++ leafJobs.filter { job =>
        job.buildable.get
      }
  }

  private def basicCredentials: IO[BasicCredentials] = {
    for {
      config   <- settings.fetch
      password <- credentials.read(JenkinsAccount(config.username))
    } yield BasicCredentials(config.username, password)
  }
}

package alfred.jenkins
import java.util.concurrent.TimeUnit

import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource}
import cats.implicits._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import io.circe.generic.semiauto._

import scala.concurrent.ExecutionContext

case class AlfredJenkinsSettings(url: String, username: String)
object AlfredJenkinsSettings {
  implicit val encoder: Encoder[AlfredJenkinsSettings] = deriveEncoder
  implicit val decoder: Decoder[AlfredJenkinsSettings] = deriveDecoder
}

trait AlfredJenkinsModule {
  def client: Client[IO]
  def environment: AlfredEnvironment
  def credentials: Credentials

  implicit def contextShift: ContextShift[IO]

  private lazy val Settings      = new Settings[AlfredJenkinsSettings](environment)
  private lazy val JenkinsCache  = new JenkinsCache(environment)
  private lazy val JenkinsClient = new JenkinsClient(client, Settings, credentials)
  private lazy val Jenkins       = new Jenkins(JenkinsClient, JenkinsCache)

  private lazy val BrowseCommand = new BrowseCommand(Jenkins, Settings)
  private lazy val SearchCommand = new SearchCommand(Jenkins, Settings)
  private lazy val LoginCommand  = new LoginCommand(Settings, credentials)
  lazy val CommandDispatcher     = new CommandDispatcher(BrowseCommand, SearchCommand, LoginCommand)
}

object AlfredJenkins extends IOApp { self =>

  private val log = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {
    val action = CliParser.parse(args, sys.env) match {
      case Left(help) =>
        IO(println(help.toString())) //log.info(s"Command parsing failed: $help") //TODO: report error as json
      case Right(args) => module.use(runCli(args))
    }

    for {
      start <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      _ <- action.onError {
        case e: Throwable => log.error(e)(s"alfred-jenkins failed processing $args")
      }
      end <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      _   <- log.info(s"Execution took: ${end - start} ms")
    } yield ExitCode.Success
  }

  private def runCli(args: Args)(module: AlfredJenkinsModule): IO[Unit] = {
    module.CommandDispatcher.dispatch(args).flatMap(write)
  }

  /**
    * Format the provided script filter result as json and print it to standard output
    */
  private def write(filter: ScriptFilter): IO[Unit] = {
    val json = filter.asJson.deepDropNullValues.spaces2
    log.debug(s"Output:\n $json") *> IO(println(json))
  }

  private val module: Resource[IO, AlfredJenkinsModule] = {
    for {
      clientValue <- BlazeClientBuilder[IO](ExecutionContext.global).resource
      environmentValue <- Resource.liftF(
        AlfredEnvironment.fromEnv.liftTo[IO].flatTap(env => log.info(s"AlfredEnvironment: $env")))
      credentialsValue <- Resource.liftF(Credentials.create(environmentValue.workflowBundleId))
    } yield {
      new AlfredJenkinsModule {
        override def client: Client[IO]                      = clientValue
        override implicit def contextShift: ContextShift[IO] = self.contextShift
        override def environment: AlfredEnvironment          = environmentValue
        override def credentials: Credentials                = credentialsValue
      }
    }
  }

  def toItems(jobs: List[JenkinsJob]): List[Item] = {
    jobs.map { job =>
      Item(
        title = job.displayName,
        subtitle = Some(job.fullDisplayName),
        icon = Some(Icon(path = icon(job))),
        `match` = Some(toMatchString(job)),
        variables = Map(
          "path" -> job.url
        )
      )
    }
  }

  private def toMatchString(job: JenkinsJob): String = {
    job.fullDisplayName
      .replaceAll("-", " ")
      .replaceAll("_", " ")
      .replaceAll("»", "/")
  }

  def icon(job: JenkinsJob): String = job._class match {
    case JobType.Root   => ""
    case JobType.Folder => Icons.Folder
    case JobType.WorkflowJob | JobType.FreestyleJob => {
      val healthOpt = job.healthReport.headOption.map(_.score)
      healthOpt match {
        case Some(health) => {
          if (health >= 0 && health <= 20) {
            Icons.Health00to19
          } else if (health > 20 && health <= 40) {
            Icons.Health20to29
          } else if (health > 40 && health <= 60) {
            Icons.Health40to59
          } else if (health > 60 && health <= 80) {
            Icons.Health60to79
          } else {
            Icons.Health80plus
          }
        }
        case None => ""
      }

    }
    case JobType.MultiBranch     => Icons.Folder
    case JobType.Unrecognised(_) => Icons.Warning
  }
}

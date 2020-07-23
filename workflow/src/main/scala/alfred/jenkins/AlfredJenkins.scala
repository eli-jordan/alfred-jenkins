package alfred.jenkins
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource, Timer}
import cats.implicits._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import io.circe.generic.semiauto._

import scala.concurrent.ExecutionContext

/**
  * Persistent settings for this workflow.
  *
  * @param url the jenkins base URL
  * @param username the username to authenticate with.
  *                 Note: The password is stored in the keychain
  */
case class AlfredJenkinsSettings(url: String, username: String)
object AlfredJenkinsSettings {
  implicit val encoder: Encoder[AlfredJenkinsSettings] = deriveEncoder
  implicit val decoder: Decoder[AlfredJenkinsSettings] = deriveDecoder
}


/**
  * Wiring all necessary components together.
  */
trait AlfredJenkinsModule {
  def client: Client[IO]
  def environment: AlfredEnvironment
  def credentials: CredentialService

  implicit def contextShift: ContextShift[IO]
  implicit def timer: Timer[IO]

  private lazy val CacheFileService = new FileService(Paths.get(environment.workflowCacheDir))
  private lazy val DataFileService  = new FileService(Paths.get(environment.workflowDataDir))
  private lazy val Settings         = new SettingsLive[AlfredJenkinsSettings](DataFileService)
  private lazy val JenkinsCache     = new JenkinsCache(CacheFileService, timer.clock)
  private lazy val JenkinsClient    = new JenkinsClientLive(client, Settings, credentials)
  private lazy val Jenkins          = new JenkinsLive(JenkinsClient, JenkinsCache)

  private lazy val BuildHistoryCommand = new BuildHistoryCommand(Jenkins)
  private lazy val BrowseCommand       = new BrowseCommand(Jenkins, Settings)
  private lazy val SearchCommand       = new SearchCommand(Jenkins, Settings)
  private lazy val LoginCommand        = new LoginCommand(Settings, credentials)

  lazy val CommandDispatcher = new CommandDispatcher(
    BrowseCommand,
    SearchCommand,
    LoginCommand,
    BuildHistoryCommand
  )
}

/**
  * The main entry point of the application
  */
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
      credentialsValue <- Resource.liftF(CredentialService.create(environmentValue.workflowBundleId))
    } yield {
      new AlfredJenkinsModule {
        override def client: Client[IO]                      = clientValue
        override implicit def contextShift: ContextShift[IO] = self.contextShift
        override def environment: AlfredEnvironment          = environmentValue
        override def credentials: CredentialService          = credentialsValue
        override implicit def timer: Timer[IO]               = self.timer
      }
    }
  }
}

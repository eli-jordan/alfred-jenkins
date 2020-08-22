package alfred.jenkins
import java.util.concurrent.TimeUnit

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.http4s.client.blaze.BlazeClientBuilder
import cats.implicits._

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
  * The main entry point of the application
  */
object AlfredJenkins extends IOApp { self =>

  private val log = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {
    val action = CliParser.parse(args, sys.env) match {
      case Left(help) =>
        IO.raiseError(new Exception(help.toString()))
      case Right(args) => mainModule.use(runCli(args))
    }

    for {
      _     <- log.debug(s"Raw args: $args")
      start <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      _     <- action
      end   <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      _     <- log.info(s"Execution took: ${end - start} ms")
    } yield ExitCode.Success
  }

  private def runCli(args: Args)(module: AlfredJenkinsModule): IO[Unit] = {
    module.CommandDispatcher
      .dispatch(args)
      .recoverWith {
        case AlfredFailure(items) => IO.pure(items)
        case e                    => log.error(e)("An unexpected error occurred") *> IO.pure(unexpectedErrorItems(e))
      }
      .flatMap(write)
  }

  /**
    * Formats an item that will be displayed when an error without specific handling is encountered.
    *
    * When the item is triggered it will open the workflow logs.
    */
  private def unexpectedErrorItems(e: Throwable): ScriptFilter = {
    ScriptFilter(
      items = List(
        Item(
          title = "An unexpected error occurred",
          subtitle = Some(s"${e.getClass.getSimpleName}: ${e.getMessage}"),
          icon = Some(Icon(path = Icons.Error)),
          valid = false
        ),
        Item(
          title = "Open Logs",
          icon = Some(Icon(path = Icons.Help)),
          variables = Map(
            "action" -> "logs"
          )
        )
      )
    )
  }

  /**
    * Format the provided script filter result as json and print it to standard output
    */
  private def write(filter: ScriptFilter): IO[Unit] = {
    val json = filter.asJson.deepDropNullValues.spaces2
    log.debug(s"Output:\n $json") *> IO(println(json))
  }

  private val mainModule: Resource[IO, AlfredJenkinsModule] = {
    for {
      clientValue <- BlazeClientBuilder[IO](ExecutionContext.global).resource
      environmentValue <-
        Resource.liftF(AlfredEnvironment.fromEnv.liftTo[IO].flatTap(env => log.info(s"AlfredEnvironment: $env")))
      credentialsValue <- Resource.liftF(CredentialService.create(environmentValue.workflowBundleId))
    } yield {
      new AlfredJenkinsModuleLive(clientValue, environmentValue, credentialsValue)
    }
  }
}

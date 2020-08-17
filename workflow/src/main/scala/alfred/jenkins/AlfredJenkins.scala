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
      start <- timer.clock.realTime(TimeUnit.MILLISECONDS)
      _ <- action.attempt.flatMap {
        case Left(e) => {
          for {
            _ <- log.error(e)(s"alfred-jenkins failed processing $args")
            _ <- write(ScriptFilter(
              items = List(
                Item(
                  title = s"An unexpected error occurred. ${e.getMessage}",
                  subtitle = Some("↩ to open logs"),
                  icon = Some(Icon(path = Icons.Error)),
                  variables = Map(
                    "action" -> "logs"
                  )
                )
              )
            ))
          } yield ()
        }
        case Right(_) => IO.unit
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

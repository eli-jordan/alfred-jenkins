package alfred.jenkins

import cats.effect.{ContextShift, IO}
import org.http4s.Uri
import cats.implicits._
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger

class Validation(settings: Settings[AlfredJenkinsSettings]) {
  def validateSettings: IO[AlfredJenkinsSettings] = {
    for {
      configOpt <- settings.fetch
      config <- {
        configOpt match {
          case Some(config) => IO.pure(config)
          case None         => IO.raiseError(AlfredFailure(JenkinsItem.noSettingsItems))
        }
      }
    } yield config
  }
}

/**
  * Command handler for the 'browse' sub-command.
  *
  * Displays all jobs one level beneath the specified path
  *
  * See [[BrowseArgs]] and [[CliParser.browseCommand]]
  */
class BrowseCommand(jenkins: Jenkins, validation: Validation) {
  def browse(pathOpt: Option[String]): IO[ScriptFilter] = {
    for {
      config <- validation.validateSettings
      path = pathOpt.getOrElse(config.url)
      filter <- jenkins.jobs(Uri.unsafeFromString(path)).map { jobs =>
        ScriptFilter(
          items = JenkinsItem.Job.items(jobs)
        )
      }
    } yield filter
  }
}

/**
  * Command handler for the 'build-history' sub-command.
  *
  * Displays the last 20 builds for a specified job.
  *
  * See [[BuildHistoryArgs]] and [[CliParser.buildHistoryCommand]]
  */
class BuildHistoryCommand(jenkins: Jenkins) {
  def history(path: String): IO[ScriptFilter] = {
    for {
      history <- jenkins.builds(Uri.unsafeFromString(path))
    } yield {
      ScriptFilter(
        items = JenkinsItem.Builds.items(history)
      )
    }
  }
}

/**
  * Command handler for the 'search' sub-command
  *
  * See [[SearchArgs]] and [[CliParser.searchCommand]]
  */
class SearchCommand(jenkins: Jenkins, validation: Validation)(implicit cs: ContextShift[IO]) {
  def search(pathOpt: Option[String]): IO[ScriptFilter] = {
    for {
      config <- validation.validateSettings
      path = pathOpt.getOrElse(config.url)
      jobs <- jenkins.scan(Uri.unsafeFromString(path))
    } yield {
      ScriptFilter(
        items = JenkinsItem.Job.items(jobs)
      )
    }
  }
}

case class JenkinsAccount(username: String) extends Account {
  override def name: String = username
}

/**
  * Command handler for the 'login' sub-command
  *
  * Saves the jenkins base URL and authentication information.
  *
  * See [[LoginArgs]] and [[CliParser.loginCommand]]
  */
class LoginCommand(settings: Settings[AlfredJenkinsSettings], credentials: CredentialService, client: JenkinsClient) {

  private val log = Slf4jLogger.getLogger[IO]

  def login(url: String, username: String, password: String): IO[ScriptFilter] = {
    for {
      _ <- validate(url, username, password)
      _ <- save(url, username, password)
    } yield JenkinsItem.successfulLoginItems
  }

  private def validate(url: String, username: String, password: String): IO[Unit] = {
    val credentials = JenkinsCredentials(username, password)
    val uri         = Uri.unsafeFromString(url)
    client
      .listJobs(uri, credentials)
      .recoverWith {
        case e =>
          log
            .warn(e)(s"Failed to validate credentials. username=$username password=$password url=$url")
            .flatMap { _ =>
              IO.raiseError(AlfredFailure(JenkinsItem.failedLoginItems))
            }
      }
      .void
  }

  private def save(url: String, username: String, password: String): IO[Unit] = {
    for {
      _ <- settings.save(AlfredJenkinsSettings(url = url, username = username))
      _ <- credentials.save(JenkinsAccount(username), password)
    } yield ()
  }
}

/**
  * Dispatches to different command handlers based on the parsed arguments.
  */
class CommandDispatcher(
    browse: BrowseCommand,
    search: SearchCommand,
    login: LoginCommand,
    history: BuildHistoryCommand
) {
  def dispatch(args: Args): IO[ScriptFilter] =
    args match {
      case LoginArgs(url, username, password) => login.login(url, username, password)
      case BrowseArgs(path)                   => browse.browse(path)
      case SearchArgs(path)                   => search.search(path)
      case BuildHistoryArgs(path)             => history.history(path)
    }
}

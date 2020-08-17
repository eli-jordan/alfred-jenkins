package alfred.jenkins

import cats.effect.{ContextShift, IO}
import org.http4s.Uri

/**
  * Command handler for the 'browse' sub-command.
  *
  * Displays all jobs one level beneath the specified path
  *
  * See [[BrowseArgs]] and [[CliParser.browseCommand]]
  */
class BrowseCommand(jenkins: Jenkins, settings: Settings[AlfredJenkinsSettings]) {
  def browse(pathOpt: Option[String]): IO[ScriptFilter] = {
    for {
      configOpt <- settings.fetch
      filter <- {
        configOpt match {
          case Some(config) => {
            val path = pathOpt.getOrElse(config.url)
            jenkins.jobs(Uri.unsafeFromString(path)).map { jobs =>
              ScriptFilter(
                items = JenkinsItem.Job.items(jobs)
              )
            }
          }
          case None => {
            IO.pure(
              ScriptFilter(
                items = List(noSettingsItem)
              ))
          }
        }
      }
    } yield filter
  }

  def noSettingsItem: Item =
    Item(
      title = "No jenkins servers available",
      subtitle = Some("Login to a server using jenkins-login <url> <username> <password>"),
      icon = Some(Icon(path = Icons.Warning)),
      variables = Map(
        "action" -> "login"
      )
    )
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
class SearchCommand(jenkins: Jenkins, settings: Settings[AlfredJenkinsSettings])(implicit cs: ContextShift[IO]) {
  def search(pathOpt: Option[String]): IO[ScriptFilter] = {
    for {
      config <- settings.fetch
      path = pathOpt.getOrElse(config.get.url) //TODO Option.get
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
class LoginCommand(settings: Settings[AlfredJenkinsSettings], credentials: CredentialService) {
  // TODO: Validate credentials
  def login(url: String, username: String, password: String): IO[ScriptFilter] = {
    for {
      _ <- settings.save(AlfredJenkinsSettings(url = url, username = username))
      _ <- credentials.save(JenkinsAccount(username), password)
    } yield
      ScriptFilter(items =
        List(
          Item(
            title = "",
            variables = Map(
              "action" -> "browse"
            )
          )
        )
      )
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

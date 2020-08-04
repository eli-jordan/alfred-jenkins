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
      config <- settings.fetch
      path = pathOpt.getOrElse(config.url)
      jobs <- jenkins.jobs(Uri.unsafeFromString(path))
    } yield {
      ScriptFilter(
        items = JenkinsItem.Job.items(jobs)
      )
    }
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
class SearchCommand(jenkins: Jenkins, settings: Settings[AlfredJenkinsSettings])(implicit cs: ContextShift[IO]) {
  def search(pathOpt: Option[String]): IO[ScriptFilter] = {
    for {
      config <- settings.fetch
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
class LoginCommand(settings: Settings[AlfredJenkinsSettings], credentials: CredentialService) {
  def login(url: String, username: String, password: String): IO[ScriptFilter] = {
    for {
      _ <- settings.save(AlfredJenkinsSettings(url = url, username = username))
      _ <- credentials.save(JenkinsAccount(username), password)
    } yield ScriptFilter(items = List.empty) //TODO: show browse results
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

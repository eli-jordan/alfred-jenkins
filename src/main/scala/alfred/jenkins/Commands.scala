package alfred.jenkins

import cats.effect.{ContextShift, IO}
import org.http4s.Uri

class BrowseCommand(jenkins: Jenkins, settings: Settings[AlfredJenkinsSettings]) {
  def browse(pathOpt: Option[String]): IO[ScriptFilter] = {
    for {
      config <- settings.fetch
      path = pathOpt.getOrElse(config.url)
      jobs <- jenkins.fetch(Uri.unsafeFromString(path))
    } yield {
      ScriptFilter(
        items = AlfredJenkins.toItems(jobs)
      )
    }
  }
}

class SearchCommand(jenkins: Jenkins, settings: Settings[AlfredJenkinsSettings])(implicit cs: ContextShift[IO]) {
  def search(pathOpt: Option[String]): IO[ScriptFilter] = {
    for {
      config <- settings.fetch
      path = pathOpt.getOrElse(config.url)
      jobs <- jenkins.scan(Uri.unsafeFromString(path))
    } yield {
      ScriptFilter(
        items = AlfredJenkins.toItems(jobs)
      )
    }
  }
}

case class JenkinsAccount(username: String) extends Account {
  override def name: String = username
}

class LoginCommand(
    settings: Settings[AlfredJenkinsSettings],
    credentials: Credentials
) {
  def login(url: String, username: String, password: String): IO[ScriptFilter] = {
    for {
      _ <- settings.save(AlfredJenkinsSettings(url = url, username = username))
      _ <- credentials.save(JenkinsAccount(username), password)
    } yield ScriptFilter(items = List.empty) //TODO: show browse results
  }
}

class CommandDispatcher(browse: BrowseCommand, search: SearchCommand, login: LoginCommand) {
  def dispatch(args: Args): IO[ScriptFilter] = args match {
    case LoginArgs(url, username, password) => login.login(url, username, password)
    case BrowseArgs(path)                   => browse.browse(path)
    case SearchArgs(path)                   => search.search(path)
    case NoArgs =>
      IO.pure(
        ScriptFilter(
          items = List(
            Item(
              title = "browse",
              autocomplete = Some("browse"),
              valid = false
            ),
            Item(
              title = "search",
              autocomplete = Some("search"),
              valid = false
            )
          )))
  }
}

package alfred.jenkins

import com.monovore.decline.{Command, Help, Opts}
import cats.implicits._

sealed trait Args
case class LoginArgs(url: String, username: String, password: String) extends Args
case class BrowseArgs(path: Option[String])                           extends Args
case class SearchArgs(path: Option[String])                           extends Args
case class BuildHistoryArgs(path: String)                             extends Args

object CliParser {

  private val loginCommand = Command(
    name = "login",
    header = "Authenticate with jenkins, saving the username and password in the keychain"
  ) {
    val url      = Opts.argument[String]("url")
    val username = Opts.argument[String]("username")
    val password = Opts.argument[String]("password")

    (url, username, password).mapN(LoginArgs)
  }

  val PathOptionName = "path"
  private val pathOption = Opts
    .env[String](
      name = PathOptionName,
      help = "The path to start browsing from"
    )

  val browseCommand = Command(
    name = "browse",
    header = "Browse the hierarchy of jenkins jobs"
  ) {
    pathOption.orNone.map(BrowseArgs)
  }

  val buildHistoryCommand = Command(
    name = "build-history",
    header = "Show the build history for the specified job"
  ) {
    pathOption.map(BuildHistoryArgs)
  }

  val searchCommand = Command(
    name = "search",
    header = "Search all jobs below a given path"
  ) {
    pathOption.orNone.map(SearchArgs)
  }

  private val mainCommand = Command(
    name = "alfred-jenkins",
    header = "Alfred script filter for interacting with jenkins"
  ) {
    Opts.subcommand(loginCommand) orElse
      Opts.subcommand(browseCommand) orElse
      Opts.subcommand(searchCommand) orElse
      Opts.subcommand(buildHistoryCommand)
  }

  def parse(args: List[String], env: Map[String, String]): Either[Help, Args] = {
    mainCommand.parse(args, env)
  }
}

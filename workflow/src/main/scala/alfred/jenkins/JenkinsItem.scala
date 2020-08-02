package alfred.jenkins

import java.time.{Instant, ZoneId}
import java.time.format.{DateTimeFormatter, FormatStyle}
import java.util.Locale

import scala.concurrent.duration._

/**
  * This is where we transform from the jenkins API response representations to
  * alfred items.
  */
object JenkinsItem {

  /**
    * The alfred-jenkins command is invoked recursively i.e. is most cases when selecting
    * an item from the alfred menu, it will re-invoke the workflow using an "external action".
    *
    * The selected items variables are passed to the new invocation.
    *
    * There are two variables that are in use.
    *
    * Path [[Variables.PathVarName]] - This controls the current location, in the jenkins job tree.
    *   e.g. https://jenkins.com/job/Folder/job/TestJob/. The navigation will only show jobs / builds
    *   that are descendants of the this path.
    *
    * Action [[Variables.ActionVarName]] - This controls the sub-command that is executed in the recursive
    *   invocation. This is used to select between the browse jobs mode (i.e. 'browse' sub-command) and the
    *   build history view (i.e. 'build-history' sub-command).
    *
    */
  object Variables {
    val PathVarName: String    = CliParser.PathOptionName
    val OpenUrlVarName: String = "openUrl"
    val ActionVarName: String  = "action"

    val BrowseCommandName: String       = CliParser.browseCommand.name
    val BuildHistoryCommandName: String = CliParser.buildHistoryCommand.name
  }

  object Job {
    def items(jobs: List[JenkinsJob]): List[Item] =
      jobs.map(item)

    private[jenkins] def item(job: JenkinsJob): Item = {
      val action = if (job._class.canHaveChildren) {
        Variables.BrowseCommandName
      } else {
        Variables.BuildHistoryCommandName
      }
      Item(
        title = job.displayName,
        subtitle = Some(job.fullDisplayName),
        icon = Some(Icon(path = icon(job))),
        `match` = Some(toMatchString(job)),
        variables = Map(
          Variables.PathVarName    -> job.url,
          Variables.OpenUrlVarName -> job.url,
          Variables.ActionVarName  -> action
        )
      )
    }

    /**
      * The alfred fuzzy search matches on whitespace separated word boundaries,
      * so make the match text better for searching.
      */
    private def toMatchString(job: JenkinsJob): String = {
      job.fullDisplayName
        .replaceAll("-", " ")
        .replaceAll("_", " ")
        .replaceAll("»", "/")
    }

    private def icon(job: JenkinsJob): String = job._class match {
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

  object Builds {
    def items(history: JenkinsBuildHistory): List[Item] = {
      history.builds.map(build => item(build, history.url))
    }

    private[jenkins] def item(build: JenkinsBuild, parentUrl: String): Item = {
      Item(
        title = build.displayName,
        subtitle = Some(buildSubtitle(build)),
        icon = Some(Icon(path = icon(build))),
        // Configure the variables so that if an item is selected
        // we just open the same build history again by setting
        // the path to the jobs url (not the build url) and the action
        // to build-history
        variables = Map(
          Variables.PathVarName   -> parentUrl,
          Variables.ActionVarName -> Variables.BuildHistoryCommandName,
          Variables.OpenUrlVarName -> build.url
        )
      )
    }

    private def buildSubtitle(build: JenkinsBuild): String = {
      val divider  = "｜"
      val duration = build.duration.millis.toMinutes.minutes

      val formatter =
        DateTimeFormatter
          .ofLocalizedDateTime(FormatStyle.SHORT)
          .withLocale(Locale.getDefault)
          .withZone(ZoneId.systemDefault())
      val date = formatter.format(Instant.ofEpochMilli(build.timestamp))
      s"$date $divider $duration"
    }

    private def icon(build: JenkinsBuild): String = build.result.map(_.toLowerCase) match {
      case Some("success")  => Icons.BlueBall
      case Some("failure")  => Icons.RedBall
      case Some("aborted")  => Icons.GreyBall
      case Some("unstable") => Icons.YellowBall
      case _                => Icons.GreyBall
    }
  }
}

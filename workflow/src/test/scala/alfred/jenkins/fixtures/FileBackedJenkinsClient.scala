package alfred.jenkins.fixtures

import java.nio.file.{Path, Paths}

import alfred.jenkins._
import cats.effect.IO
import cats.implicits._
import org.http4s.Uri

/**
  * An implementation of [[JenkinsClient]] that is backed by canned data that is stored in
  * a directory on the filesystem.
  */
class FileBackedJenkinsClient(files: FileService) extends JenkinsClient {

  override def listBuilds(job: Uri, credentials: JenkinsCredentials): IO[JenkinsBuildHistory] = {
    val path = buildsPath(job)
    for {
      exists <- files.exists(path)
      builds <-
        if (exists) {
          for {
            data    <- files.readFile(buildsPath(job))
            json    <- io.circe.jawn.parse(data).liftTo[IO]
            history <- json.as[JenkinsBuildHistory].liftTo[IO]
          } yield history
        } else {
          IO.pure(
            JenkinsBuildHistory(
              url = "",
              displayName = "",
              fullDisplayName = "",
              builds = List.empty
            )
          )
        }
    } yield builds
  }

  override def listJobs(path: Uri, credentials: JenkinsCredentials): IO[List[JenkinsJob]] = {
    val file = jobPath(path)
    for {
      exists <- files.exists(file)
      jobs <-
        if (exists) {
          for {
            data <- files.readFile(file)
            json <- io.circe.jawn.parse(data).liftTo[IO]
            list <- json.as[JenkinsJobsList].liftTo[IO]
          } yield list.jobs
        } else {
          IO.pure(List.empty)
        }
    } yield jobs
  }

  private def jobPath(uri: Uri): Path =
    path(uri, "jobs.json")

  private def buildsPath(uri: Uri): Path =
    path(uri, "builds.json")

  private def path(uri: Uri, file: String): Path = {
    val host = uri.host.get.value
    val path = uri.path

    val normalised = if (path.endsWith("/")) {
      path.dropRight(1)
    } else {
      path
    }
    Paths.get(s"$host/$normalised/$file")
  }
}

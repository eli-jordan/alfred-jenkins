package alfred.jenkins

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit

import cats.effect.{Clock, ContextShift, IO}
import cats.implicits._
import io.circe.Decoder
import io.circe.syntax._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{BasicCredentials, MediaType, Request, Uri}

import scala.concurrent.duration._

/**
  * Combines the [[JenkinsClient]] and the [[JenkinsCache]] into a uniform interface for querying
  * that ensures the cache is populated on read.
  *
  * @param client The jenkins api client
  * @param cache the jenkins cache
  */
class Jenkins(
    client: JenkinsClient,
    cache: JenkinsCache,
    validation: Validation,
    credentials: CredentialService
)(implicit cs: ContextShift[IO]) {

  /**
    * Fetch jobs one level below the provided path.
    *
    * Note: The `path` parameter is assumed to be a valid, fully qualified job path.
    *       e.g. https://jenkins.com/job/MyFolder
    */
  def jobs(path: Uri): IO[List[JenkinsJob]] = {
    for {
      credentials <- jenkinsCredentials
      job <- cache.fetch(toCacheKey(path)).flatMap {
        case Some(jobs) => IO.pure(jobs)
        case None =>
          for {
            jobs <- client.listJobs(path, credentials)
            _    <- cache.store(toCacheKey(path), jobs)
          } yield jobs
      }
    } yield job

  }

  /**
    * Fetch the latest 20 builds for the specified job.
    *
    * Note:
    *  1. The `job` parameter is assumed to be a valid, fully qualified path.
    *  2. Since builds can be updated quite frequently, this is not cached.
    */
  def builds(job: Uri): IO[JenkinsBuildHistory] = {
    for {
      credentials <- jenkinsCredentials
      history     <- client.listBuilds(job, credentials)
    } yield history
  }

  /**
    * Recursively scans all jobs that are under the specified path, returning only the
    * leaf nodes that are found.
    *
    * Note: The `path` parameter is assumed to be a valid, fully qualified job path.
    *       e.g. https://jenkins.com/job/MyFolder
    */
  def scan(path: Uri): IO[List[JenkinsJob]] = {
    for {
      list <- jobs(path)
      (branchJobs, leafJobs) = list.partition(_._class.canHaveChildren)
      jobs <- branchJobs.parFlatTraverse { job =>
        scan(Uri.unsafeFromString(job.url))
      }
    } yield {
      jobs ++ leafJobs.filter { job =>
        job.buildable.get
      }
    }
  }

  private def toCacheKey(uri: Uri): CacheKey = {
    CacheKey(
      host = uri.host.get.value,
      path = uri.path
    )
  }

  private def jenkinsCredentials: IO[JenkinsCredentials] = {
    for {
      config   <- validation.validateSettings
      password <- credentials.read(JenkinsAccount(config.username))
    } yield JenkinsCredentials(config.username, password)
  }
}

/**
  * Interface to the Jenkins API that exposes listing jobs and build history for a job.
  */
case class JenkinsCredentials(username: String, password: String)
trait JenkinsClient {

  /**
    * List build for the provided job
    *
    * Note: The `path` parameter is assumed to be a valid, fully qualified job path.
    *        e.g. https://jenkins.com/job/MyFolder
    */
  def listBuilds(job: Uri, credentials: JenkinsCredentials): IO[JenkinsBuildHistory]

  /**
    * Fetch jobs one level below the provided path.
    *
    * Note: The `path` parameter is assumed to be a valid, fully qualified job path.
    *       e.g. https://jenkins.com/job/MyFolder
    */
  def listJobs(path: Uri, credentials: JenkinsCredentials): IO[List[JenkinsJob]]
}

/**
  * A Jenkins API client that allows fetching jobs and their build status.
  */
class JenkinsClientLive(client: Client[IO]) extends JenkinsClient {

  /**
    * Specifies the fields thar are needed for a [[JenkinsBuild]]
    */
  val BuildFilter =
    "number,displayName,fullDisplayName,description,result,url,building,timestamp,duration"

  /**
    * Specifies the fields that are needed for a [[JenkinsJobsList]]
    */
  val JobListFilter =
    s"jobs[displayName,fullDisplayName,url,color,buildable,healthReport[description,score,iconUrl],lastBuild[$BuildFilter]]"

  /**
    * Specifies the fields needed for [[JenkinsBuildHistory]]
    */
  val BuildHistoryFilter =
    s"displayName,fullDisplayName,url,builds[$BuildFilter]{,20}"

  override def listBuilds(job: Uri, credentials: JenkinsCredentials): IO[JenkinsBuildHistory] = {
    fetch[JenkinsBuildHistory](job, BuildHistoryFilter, credentials)
  }

  override def listJobs(path: Uri, credentials: JenkinsCredentials): IO[List[JenkinsJob]] = {
    fetch[JenkinsJobsList](path, JobListFilter, credentials).map(_.jobs)
  }

  private def fetch[A: Decoder](path: Uri, filter: String, credentials: JenkinsCredentials): IO[A] = {
    val basicCredentials = BasicCredentials(credentials.username, credentials.password)
    val uri              = path / "api" / "json" +? ("tree", filter)
    val request = Request[IO]()
      .withUri(uri)
      .withHeaders(
        Accept(MediaType.application.json),
        Authorization(basicCredentials)
      )

    client.expect[A](request)
  }
}

/**
  * Caches data retrieved from the Jenkins API as local files. This makes the latency when
  * searching and browsing jobs much lower, at the expense of not having the most up to date
  * set of jobs at all times.
  *
  * The data is stored as individual files, that correspond to the host and path of a jenkins job.
  * For example.
  *  https://jenkins.com/job/MyCachedJob would have the response data stored in
  *  <cache_dir>/jenkins.com/job/MyCachedJob/cache.json
  *
  * @param cacheTtl The time-to-live of cache entries.
  */
class JenkinsCache(files: FileService, clock: Clock[IO], cacheTtl: FiniteDuration = 1.day) {

  /**
    * Fetch an unexpired entry from the cache.
    *
    * @param key The key for the entry being looked up.
    * @return The cache entry if it exists and is not expired. Otherwise, None.
    */
  def fetch(key: CacheKey): IO[Option[List[JenkinsJob]]] = {
    isValidCacheEntry(key).flatMap {
      case true  => fetchCacheEntry(key).map(Some.apply)
      case false => IO.pure(None)
    }
  }

  /**
    * An entry is valid if it exists in the cache, and it is not older than the ttl
    */
  private def isValidCacheEntry(key: CacheKey): IO[Boolean] =
    for {
      exists <- files.exists(key.filePath)
      isValid <-
        if (exists) {
          isExpired(key).map(!_)
        } else {
          IO.pure(false)
        }
    } yield isValid

  private def isExpired(key: CacheKey): IO[Boolean] =
    for {
      modifiedEpochMillis <- files.lastModified(key.filePath)
      currentEpochMillis  <- clock.realTime(TimeUnit.MILLISECONDS)
    } yield {
      val age = currentEpochMillis - modifiedEpochMillis
      age.millis > cacheTtl
    }

  private def fetchCacheEntry(key: CacheKey): IO[List[JenkinsJob]] =
    for {
      data <- files.readFile(key.filePath)
      json <- io.circe.jawn.parse(data).liftTo[IO]
      jobs <- json.as[List[JenkinsJob]].liftTo[IO]
    } yield jobs

  /**
    * Store a cache entry.
    *
    * @param key The key for the entry being stored
    * @param data the data to be stored.
    * @return
    */
  def store(key: CacheKey, data: List[JenkinsJob]): IO[Unit] =
    files.writeFile(key.filePath, data.asJson.noSpaces)
}

case class CacheKey(host: String, path: String) {
  lazy val filePath: Path = {
    val normalised = if (path.endsWith("/")) {
      path.dropRight(1)
    } else {
      path
    }
    Paths.get(s"$host/$normalised/cache.json")
  }
}

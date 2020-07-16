package alfred.jenkins

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import cats.effect.{ContextShift, IO}
import cats.implicits._
import io.circe.syntax._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{BasicCredentials, MediaType, Request, Uri}

import scala.concurrent.duration._

/**
  * Combines the [[JenkinsClient]] and the [[JenkinsCache]] into a uniform interface for querying
  * that ensure the cache is populated on read.
  *
  * @param client The jenkins api client
  * @param cache the jenkins cache
  */
class Jenkins(client: JenkinsClient, cache: JenkinsCache)(implicit cs: ContextShift[IO]) {

  /**
    * Fetch jobs one level below the provided path.
    *
    * Note: The `path` parameter is assumed to be a valid, fully qualified job path.
    *       e.g. https://jenkins.com/job/MyFolder
    */
  def fetch(path: Uri): IO[List[JenkinsJob]] = {
    cache.fetch(toCacheKey(path)).flatMap {
      case Some(jobs) => IO.pure(jobs)
      case None =>
        for {
          jobs <- client.fetch(path)
          _    <- cache.store(toCacheKey(path), jobs)
        } yield jobs
    }
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
      list <- fetch(path)
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
}

/**
  * A Jenkins API client that allows fetching jobs and their build status.
  *
  * @param client the http4s client used to issue requests
  * @param settings the settings accessor used to lookup the jenkins server URL and the user name to authenticate with.
  * @param credentials the credentials accessor used to lookup the users password.
  */
class JenkinsClient(client: Client[IO], settings: Settings[AlfredJenkinsSettings], credentials: Credentials) {
  val filter =
    "jobs[displayName,fullDisplayName,url,color,buildable,healthReport[description,score,iconUrl],lastBuild[building,result,displayName,fullDisplayName,url]]"

  /**
    * Fetch jobs one level below the provided path.
    *
    * Note: The `path` parameter is assumed to be a valid, fully qualified job path.
    *       e.g. https://jenkins.com/job/MyFolder
    */
  def fetch(path: Uri): IO[List[JenkinsJob]] = {
    for {
      credentials <- basicCredentials
      uri = path / "api" / "json" +? ("tree", filter)
      request = Request[IO]()
        .withUri(uri)
        .withHeaders(
          Accept(MediaType.application.json),
          Authorization(credentials)
        )
      jobs <- client.expect[JenkinsJobsList](request).map(_.jobs)
    } yield jobs
  }

  private def basicCredentials: IO[BasicCredentials] = {
    for {
      config   <- settings.fetch
      password <- credentials.read(JenkinsAccount(config.username))
    } yield BasicCredentials(config.username, password)
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
  *  ${cache_dir}/jenkins.com/job/MyCachedJob/cache.json
  *
  * @param env The environment. This is just used to extract the cache directory
  * @param cacheTtl The time-to-live of cache entries.
  */
class JenkinsCache(env: AlfredEnvironment, cacheTtl: FiniteDuration = 5.minutes) {

  /**
    * Fetch an unexpired entry from the cache.
    *
    * @param key The key for the entry being looked up.
    * @return The cache entry if it exists and is not expired. Otherwise, None.
    */
  def fetch(key: CacheKey): IO[Option[List[JenkinsJob]]] = IO {

    def isExpired(file: File): Boolean = {
      val age = System.currentTimeMillis() - file.lastModified()
      age.millis > cacheTtl
    }

    val file = new File(env.workflowCacheDir, key.filePath)
    if (file.exists() && file.isFile && !isExpired(file)) {
      val cacheE = for {
        json     <- io.circe.jawn.parseFile(file)
        settings <- json.as[List[JenkinsJob]]
      } yield settings
      Some(cacheE.right.get)
    } else {
      None
    }
  }

  /**
    * Store a cache entry.
    *
    * @param key The key for the entry being stored
    * @param data the data to be stored.
    * @return
    */
  def store(key: CacheKey, data: List[JenkinsJob]): IO[Unit] = IO {
    val json = data.asJson.noSpaces
    val file = Paths.get(env.workflowCacheDir, key.filePath)
    file.toFile.getParentFile.mkdirs()
    Files.write(file, json.getBytes(StandardCharsets.UTF_8))
  }
}

case class CacheKey(host: String, path: String) {
  def filePath: String = {
    val normalised = if (path.endsWith("/")) {
      path.dropRight(1)
    } else {
      path
    }
    s"$host/$normalised/cache.json"
  }
}

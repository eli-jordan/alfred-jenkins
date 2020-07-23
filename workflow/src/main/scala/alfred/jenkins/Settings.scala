package alfred.jenkins

import java.nio.file.Paths

import cats.effect.IO
import cats.implicits._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

trait Settings[S] {
  def save(settings: S): IO[Unit]
  def fetch: IO[S]
}

/**
 * Responsible for reading and saving settings data for the workflow.
 * Settings are stored as a single json encoded file.
 */
class SettingsLive[S: Encoder: Decoder](files: FileService) extends Settings[S] {

  private val settingsFile = Paths.get("settings.json")

  /**
   * Serialize and persist the settings.
   *
   * @param settings the settings object
   */
  def save(settings: S): IO[Unit] = {
    val json = settings.asJson.spaces2
    files.writeFile(settingsFile, json)
  }

  /**
   * Lookup and deserialize the settings
   */
  def fetch: IO[S] = {
    for {
      data     <- files.readFile(settingsFile)
      json     <- io.circe.jawn.parse(data).liftTo[IO]
      settings <- json.as[S].liftTo[IO]
    } yield settings
  }
}

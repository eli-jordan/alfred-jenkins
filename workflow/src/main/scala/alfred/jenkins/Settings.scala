package alfred.jenkins

import java.nio.file.Paths

import cats.effect.IO
import cats.implicits._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}

trait Settings[S] {

  /**
    * Serialize and persist the settings.
    *
    * @param settings the settings object
    */
  def save(settings: S): IO[Unit]

  /**
    * Lookup and deserialize the settings
    */
  def fetch: IO[S]
}

/**
  * Responsible for reading and saving settings data for the workflow.
  * Settings are stored as a single json encoded file.
  */
class SettingsLive[S: Encoder: Decoder](files: FileService, fileName: String = "settings.json") extends Settings[S] {

  private lazy val settingsFile = Paths.get(fileName)

  override def save(settings: S): IO[Unit] = {
    val json = settings.asJson.spaces2
    files.writeFile(settingsFile, json)
  }

  override def fetch: IO[S] = {
    for {
      data     <- files.readFile(settingsFile)
      json     <- io.circe.jawn.parse(data).liftTo[IO]
      settings <- json.as[S].liftTo[IO]
    } yield settings
  }
}

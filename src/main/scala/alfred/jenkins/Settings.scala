package alfred.jenkins

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import cats.implicits._

class Settings[S: Encoder: Decoder](env: AlfredEnvironment) {

  private val settingsFile = s"${env.workflowDataDir}/settings.json"

  def save(settings: S): IO[Unit] = IO {
    val json = settings.asJson.spaces2
    Files.write(Paths.get(settingsFile), json.getBytes(StandardCharsets.UTF_8))
  }

  def fetch: IO[S] = {
    val settingsE = for {
      json <- io.circe.jawn.parseFile(new File(settingsFile))
      settings <- json.as[S]
    } yield settings

    settingsE.liftTo[IO]
  }
}


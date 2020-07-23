package alfred.jenkins

import java.time.Instant

import alfred.jenkins.fixtures.FileFixture
import cats.effect.IO
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class SettingsSpec extends AnyFlatSpec with Matchers {

  case class TestSettings(
      name: String,
      dates: List[Instant]
  )
  object TestSettings {
    implicit val encoder: Encoder[TestSettings] = deriveEncoder
    implicit val decoder: Decoder[TestSettings] = deriveDecoder
  }

  it should "save and fetch settings" in {
    val testConfig = TestSettings(
      name = "foo-bar-beaver",
      dates = (0 to 5).toList.map(_ => Instant.now())
    )

    FileFixture.directory
      .use { dir =>
        val settings = new SettingsLive[TestSettings](new FileService(dir.toPath))
        for {
          _       <- settings.save(testConfig)
          fetched <- settings.fetch
          _ <- IO {
            fetched mustBe testConfig
          }
        } yield ()
      }
      .unsafeRunSync()
  }
}

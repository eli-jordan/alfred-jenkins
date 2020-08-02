package alfred.jenkins

import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class CredentialServiceSpec extends AnyFlatSpec with Matchers {

  it should "create / read / delete" in {
    object TestAccount extends Account { val name = "TestAccount" }

    val action = for {
      service <- CredentialService.create("CredentialServiceSpec")
      _ <- service.save(TestAccount, "foo-bar-badger")
      pwd <- service.read(TestAccount)
      _ <- IO { pwd mustBe "foo-bar-badger" }
      _ <- service.delete(TestAccount)
    } yield ()

    action.unsafeRunSync()
  }
}

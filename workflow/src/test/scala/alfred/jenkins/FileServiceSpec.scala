package alfred.jenkins

import java.nio.file.Paths

import alfred.jenkins.fixtures.FileFixture
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class FileServiceSpec extends AnyFlatSpec with Matchers {

  behavior of "FileService"

  it should "Create / exists / read" in {
    FileFixture.directory.use { dir =>
      val service = new FileService(dir.toPath)

      for {
        _ <- service.writeFile(Paths.get("foo.txt"), "this is some data")
        exists <- service.exists(Paths.get("foo.txt"))
        data <- service.readFile(Paths.get("foo.txt"))
        _ <- IO {
          exists mustBe true
          data mustBe "this is some data"
        }
      } yield  ()

    }.unsafeRunSync()
  }

  it should "Write multiple / list" in {
    FileFixture.directory.use { dir =>
      val service = new FileService(dir.toPath)

      for {
        _ <- service.writeFile(Paths.get("abc/def/foo1.txt"), "this is some data in foo1")
        _ <- service.writeFile(Paths.get("abc/def/foo2.txt"), "this is some data in foo2")
        _ <- service.writeFile(Paths.get("abc/def/foo3.txt"), "this is some data in foo3")
        list <- service.listDir(Paths.get("abc/def"))
        _ <- IO {
          val paths = list.map(_.toString)
          paths must contain("abc/def/foo1.txt")
          paths must contain("abc/def/foo2.txt")
          paths must contain("abc/def/foo3.txt")
        }
      } yield  ()

    }.unsafeRunSync()
  }

  it should "create / check last modified" in {
    FileFixture.directory.use { dir =>
      val service = new FileService(dir.toPath)

      for {
        _ <- service.writeFile(Paths.get("test.file.text"), "this is some data in foo1")
        modifiedAt <- service.lastModified(Paths.get("test.file.text"))
        _ <- IO {
          (System.currentTimeMillis() - modifiedAt) < 1000 mustBe true
        }
      } yield  ()
    }
  }
}

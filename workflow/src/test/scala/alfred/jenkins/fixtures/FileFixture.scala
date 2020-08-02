package alfred.jenkins.fixtures

import java.io.File
import java.nio.file.Files

import cats.effect.{IO, Resource}

import scala.reflect.io.Directory

object FileFixture {

  def directory: Resource[IO, File] = {
    val create = IO { Files.createTempDirectory("FileFixture").toFile }
    def delete(file: File) = IO { new Directory(file).deleteRecursively() }.void
    Resource.make(create)(delete)
  }
}

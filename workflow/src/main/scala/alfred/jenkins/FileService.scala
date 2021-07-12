package alfred.jenkins

import java.nio.file.{Files, Path, Paths}

import cats.effect.{IO, Resource}

import scala.io.Source

class FileService(root: Path) {

  /**
    * Checks whether the file represented by the given path exists
    */
  def exists(path: Path): IO[Boolean] =
    IO {
      fromRoot(path).toFile.exists()
    }

  /**
    * If the path represents a directory, returns a listing of the files/directories
    * that are within it.
    */
  def listDir(path: Path): IO[List[Path]] =
    IO {
      fromRoot(path).toFile
        .listFiles()
        .toList
        .map(_.toPath)
        .map(root.relativize)
    }

  /**
    * Fetches the last modified time of the specified file
    */
  def lastModified(path: Path): IO[Long] =
    IO {
      if (true) {
        fromRoot(path).toFile.lastModified()
      } else {
        0L
      }
    }

  /**
    * Read the contents of the specified file as a String
    */
  def readFile(path: Path): IO[String] = {
    val acquire = IO.delay(Source.fromFile(fromRoot(path).toFile))
    val use     = (source: Source) => IO.delay(source.getLines().mkString)
    Resource.fromAutoCloseable(acquire).use(use)
  }

  /**
    * Write the provided data to the specified file.
    */
  def writeFile(pathRel: Path, data: String): IO[Unit] =
    IO {
      val path = fromRoot(pathRel)
      if (!path.getParent.toFile.exists()) {
        path.getParent.toFile.mkdirs()
      }
      Files.write(path, data.getBytes)
    }

  private def fromRoot(path: Path): Path =
    Paths.get(root.toString, path.toString)
}

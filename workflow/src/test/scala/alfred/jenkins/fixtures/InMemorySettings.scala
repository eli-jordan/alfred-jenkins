package alfred.jenkins.fixtures

import alfred.jenkins.Settings
import cats.effect.IO

class InMemorySettings[S](initial: Option[S] = None) extends Settings[S] {

  private var settings: S = initial.getOrElse(null.asInstanceOf[S])

  override def save(s: S): IO[Unit] = IO {
    this.synchronized {
      settings = s
    }

  }

  override def fetch: IO[S] = IO {
    this.synchronized(settings)
  }
}

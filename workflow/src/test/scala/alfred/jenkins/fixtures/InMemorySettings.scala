package alfred.jenkins.fixtures

import alfred.jenkins.Settings
import cats.effect.IO
import cats.effect.concurrent.Ref

/**
  * An in-memory implementation of [[Settings]] for use in tests
  */
class InMemorySettings[S](initial: S) extends Settings[S] {

  private val settings: Ref[IO, S] = Ref.unsafe[IO, S](initial)

  override def save(s: S): IO[Unit] = settings.set(s)

  override def fetch: IO[S] = settings.get
}

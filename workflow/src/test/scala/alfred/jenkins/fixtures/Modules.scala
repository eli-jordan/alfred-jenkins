package alfred.jenkins.fixtures

import java.io.File
import java.nio.file.Paths

import alfred.jenkins._
import cats.effect.{IO, Resource}
import cats.implicits._

trait JenkinsClientModuleFake extends JenkinsClientModule {
  def jenkinsDataDir: String
  override lazy val JenkinsClient: JenkinsClient =
    new FileBackedJenkinsClient(new FileService(Paths.get(jenkinsDataDir)))
}

class SystemSpecModule(
    override val Environment: AlfredEnvironment,
    override val CredentialService: CredentialService,
    override val Settings: Settings[AlfredJenkinsSettings]
) extends AlfredJenkinsModule
  with ValidationModuleLive
  with JenkinsModuleLive
  with JenkinsClientModuleFake
  with CommandModuleLive
  with FileServiceModuleLive
  with IOModule {
  override def jenkinsDataDir: String = "./workflow/src/test/resources/jenkins-responses"
}

object SystemSpecModule {

  private val defaultSettings = new InMemorySettings(
    AlfredJenkinsSettings(url = "https://jenkins.com", username = "Alice")
  )

  def resource(settings: Settings[AlfredJenkinsSettings] = defaultSettings): Resource[IO, SystemSpecModule] = {
    val credentials = CredentialService.create("SystemSpecModule").unsafeRunSync()
    credentials.save(JenkinsAccount("Alice"), "nothing").unsafeRunSync()

    def env(data: File, cache: File) =
      AlfredEnvironment(
        alfredVersion = "4",
        alfredVersionBuild = "system-spec",
        workflowBundleId = "system-spec",
        workflowCacheDir = cache.toString,
        workflowDataDir = data.toString,
        workflowName = "alfred-jenkins-system-spec",
        workflowUid = "system-spec",
        debugMode = true
      )

    (FileFixture.directory, FileFixture.directory).mapN { (dir1, dir2) =>
      new SystemSpecModule(env(dir1, dir2), credentials, settings)
    }
  }
}

package alfred.jenkins.fixtures

import java.io.File
import java.nio.file.Paths

import alfred.jenkins._
import cats.effect.{IO, Resource}
import cats.implicits._

trait SettingsModuleFake extends SettingsModule {
  override lazy val Settings: Settings[AlfredJenkinsSettings] = new InMemorySettings(
    AlfredJenkinsSettings(url = "https://jenkins.com", username = "Alice")
  )
}

trait JenkinsClientModuleFake extends JenkinsClientModule {
  def jenkinsDataDir: String
  override lazy val JenkinsClient: JenkinsClient =
    new FileBackedJenkinsClient(new FileService(Paths.get(jenkinsDataDir)))
}

class SystemSpecModule(
    override val Environment: AlfredEnvironment,
    override val CredentialService: CredentialService
) extends AlfredJenkinsModule
  with JenkinsModuleLive
  with JenkinsClientModuleFake
  with CommandModuleLive
  with FileServiceModuleLive
  with SettingsModuleFake
  with IOModule {
  override def jenkinsDataDir: String = "./workflow/src/test/resources/jenkins-responses"
}

object SystemSpecModule {
  def resource: Resource[IO, SystemSpecModule] = {
    val credentials = CredentialService.create("SystemSpecModule").unsafeRunSync()

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
      new SystemSpecModule(env(dir1, dir2), credentials)
    }
  }
}

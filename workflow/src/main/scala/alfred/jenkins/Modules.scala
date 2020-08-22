package alfred.jenkins

import java.nio.file.Paths

import cats.effect.{ContextShift, IO, Timer}
import org.http4s.client.Client

import scala.concurrent.ExecutionContext

/**
  * This file contains module definitions that control how the application is wired together.
  *
  * Naming conventions:
  * - Abstract definition of the components for <X> is named <X>Module
  * - Components that are wired in a production ready configuration are named <X>ModuleLive
  */
/**
  * Abstract definition of the jenkins available jenkins components
  */
trait JenkinsModule extends JenkinsClientModule {
  def JenkinsCache: JenkinsCache
  def Jenkins: Jenkins
}

trait JenkinsClientModule {
  def JenkinsClient: JenkinsClient
}

trait JenkinsClientModuleLive extends JenkinsClientModule with HttpClientModule {
  override lazy val JenkinsClient: JenkinsClient =
    new JenkinsClientLive(Client)
}

/**
  * Production definition of the jenkins components described by [[JenkinsModule]]
  */
trait JenkinsModuleLive
  extends JenkinsModule
  with FileServiceModule
  with ValidationModule
  with CredentialsModule
  with IOModule {

  override lazy val JenkinsCache: JenkinsCache =
    new JenkinsCache(CacheFileService, timer.clock)

  override lazy val Jenkins: Jenkins =
    new Jenkins(JenkinsClient, JenkinsCache, Validation, CredentialService)
}

/**
  * Abstract definition of the components that define the CLI sub-commands.
  */
trait CommandModule {
  def BuildHistoryCommand: BuildHistoryCommand
  def LoginCommand: LoginCommand
  def SearchCommand: SearchCommand
  def BrowseCommand: BrowseCommand

  def CommandDispatcher: CommandDispatcher
}

/**
  * Production ready configuration of the components described in [[CommandModule]]
  */
trait CommandModuleLive
  extends CommandModule
  with JenkinsModule
  with SettingsModule
  with CredentialsModule
  with ValidationModule
  with JenkinsClientModule
  with IOModule {

  override lazy val BuildHistoryCommand: BuildHistoryCommand = new BuildHistoryCommand(Jenkins)
  override lazy val LoginCommand: LoginCommand               = new LoginCommand(Settings, CredentialService, JenkinsClient)
  override lazy val SearchCommand: SearchCommand             = new SearchCommand(Jenkins, Validation)
  override lazy val BrowseCommand: BrowseCommand             = new BrowseCommand(Jenkins, Validation)

  override lazy val CommandDispatcher: CommandDispatcher = new CommandDispatcher(
    BrowseCommand,
    SearchCommand,
    LoginCommand,
    BuildHistoryCommand
  )
}

/**
  * Abstract definition of the [[FileService]] instances that are scoped to the
  * data directories available to a workflow.
  */
trait FileServiceModule {
  def CacheFileService: FileService
  def DataFileService: FileService
}

/**
  * Production ready configuration of the components described in [[FileServiceModule]]
  */
trait FileServiceModuleLive extends FileServiceModule with EnvironmentModule {

  override lazy val CacheFileService: FileService =
    new FileService(Paths.get(Environment.workflowCacheDir))

  override lazy val DataFileService: FileService =
    new FileService(Paths.get(Environment.workflowDataDir))
}

trait SettingsModule {
  def Settings: Settings[AlfredJenkinsSettings]
}

/**
  * Production ready configuration of the components described in [[SettingsModule]]
  */
trait SettingsModuleLive extends SettingsModule with FileServiceModule {
  override lazy val Settings: Settings[AlfredJenkinsSettings] =
    new SettingsLive[AlfredJenkinsSettings](DataFileService)
}

trait ValidationModule {
  def Validation: Validation
}

trait ValidationModuleLive extends ValidationModule with SettingsModule {
  override def Validation: Validation = new Validation(Settings)
}

/**
  * Utility module that contains cats-effect implicits that are needed when wiring
  * some components up.
  */
trait IOModule {
  private val executionContext                     = ExecutionContext.global
  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
  implicit lazy val timer: Timer[IO]               = IO.timer(executionContext)
}

/**
  * Abstract definition of the component that provides access to the alfred provided
  * environment variables.
  *
  * Note: There is no Live version of this module, since the creation of the value is effectful
  */
trait EnvironmentModule {
  def Environment: AlfredEnvironment
}

/**
  * Abstract definition of the component that provides access to credentials.
  *
  * Note: There is no Live version of this module, since the creation of the value is effectful
  */
trait CredentialsModule {
  def CredentialService: CredentialService
}

/**
  * Abstract definition of the component that allows making http requests
  *
  * Note: There is no Live version of this module, since the creation of the value is effectful
  */
trait HttpClientModule {
  def Client: Client[IO]
}

/**
  * Abstract definition of all the components that are needed to run the application
  */
trait AlfredJenkinsModule
  extends JenkinsModule
  with CommandModule
  with FileServiceModule
  with SettingsModule
  with EnvironmentModule
  with CredentialsModule

/**
  * Production ready configuration of the main application module.
  */
class AlfredJenkinsModuleLive(
    val Client: Client[IO],
    val Environment: AlfredEnvironment,
    val CredentialService: CredentialService
) extends AlfredJenkinsModule
  with JenkinsModuleLive
  with ValidationModuleLive
  with JenkinsClientModuleLive
  with CommandModuleLive
  with FileServiceModuleLive
  with SettingsModuleLive
  with IOModule
  with HttpClientModule

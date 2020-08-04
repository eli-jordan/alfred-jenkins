package alfred.jenkins
import pt.davidafsilva.apple.OSXKeychain

import cats.effect.IO

/**
  * Represents a named account, that will be stored in the keychain.
  */
trait Account {
  def name: String
}

/**
  * Represents a simple abstraction over the OSX keychain.
  *
  * @param service The keychain "service" name. This should be the workflow bundle id.
  * @param keychain The main interface presented by the underlying java library.
  */
class CredentialService(service: String, keychain: OSXKeychain) {

  /**
    * Save a password in the keychain.
    *
    * If a workflow needs to store credentials or sensitive information, it should be stored
    * using this function.
    *
    * @param account The account to associated the password with
    * @param password the password value
    */
  def save(account: Account, password: String): IO[Unit] =
    IO {
      if (keychain.findGenericPassword(service, account.name).isPresent) {
        keychain.modifyGenericPassword(service, account.name, password)
      } else {
        keychain.addGenericPassword(service, account.name, password)
      }
    }

  /**
    * Delete a password from the keychain.
    *
    * @param account The account to delete the password for.
    */
  def delete(account: Account): IO[Unit] =
    IO {
      keychain.deleteGenericPassword(service, account.name)
    }

  /**
    * Lookup the password for the provided account.
    *
    * @param account The account to lookup the password for.
    */
  def read(account: Account): IO[String] =
    IO {
      keychain.findGenericPassword(service, account.name).get()
    }
}

object CredentialService {
  def create(service: String): IO[CredentialService] =
    IO(OSXKeychain.getInstance).map { keyring =>
      new CredentialService(service, keyring)
    }
}

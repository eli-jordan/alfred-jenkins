package alfred.jenkins

/**
  * When an error occurs that requires information to be presented to the user, this exception
  * should be raised and the items that will be presented to the user specified.
  */
case class AlfredFailure(items: ScriptFilter) extends Exception

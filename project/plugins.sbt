addSbtPlugin("com.typesafe.sbt"  % "sbt-native-packager" % "1.7.4")
addSbtPlugin("ohnosequences"     % "sbt-github-release"  % "0.7.0")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"          % "4.1.1")

libraryDependencies ++= Seq(
  // Used by AlfredWorkflowPlugin
  "com.googlecode.plist" % "dd-plist" % "1.23"
)

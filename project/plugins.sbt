addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")
addSbtPlugin("ohnosequences"    % "sbt-github-release"  % "0.7.0")
addSbtPlugin("com.dwijnand"     % "sbt-dynver"          % "4.1.1")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"       % "1.7.3")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"        % "2.4.2")

libraryDependencies ++= Seq(
  // Used by AlfredWorkflowPlugin
  "com.googlecode.plist" % "dd-plist" % "1.23",
  // For JDK 11
  "com.sun.activation" % "javax.activation" % "1.2.0"
)

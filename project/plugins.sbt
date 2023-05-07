addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.8.1")
addSbtPlugin("ohnosequences"    % "sbt-github-release"  % "0.7.0")
addSbtPlugin("com.github.sbt"   % "sbt-dynver"          % "5.0.1")
addSbtPlugin("org.scoverage"    % "sbt-scoverage"       % "1.9.2")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"        % "2.4.5")
addSbtPlugin("com.sonar-scala"  % "sbt-sonar"           % "2.3.0")

libraryDependencies ++= Seq(
  // Used by AlfredWorkflowPlugin
  "com.googlecode.plist" % "dd-plist" % "1.23",
  // For JDK 11
  "com.sun.activation" % "javax.activation" % "1.2.0"
)

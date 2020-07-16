ThisBuild / scalaVersion := "2.13.2"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.elijordan"

val circeVersion = "0.13.0"

val initAtBuild = List(
  "scala.Predef$",
  "scala.reflect.ClassTag$",
  "scala.math.BigInt$",
  "scala.concurrent.duration.Duration$",
  "scala.package$",
  "scala.collection.immutable.IndexedSeq$",
  "scala.collection.Iterable$",
  "scala.reflect.ManifestFactory$",
  "scala.reflect.Manifest$",
  "scala.collection.immutable.LazyList$",
  "scala.collection.immutable.Iterable$",
  "scala.collection.immutable.List$",
  "scala.collection.immutable.Seq$",
  "scala.collection.immutable.Vector$"
)

/**
  * Graal Native Image Notes
  * ---
  *
  * > Config was boot strapped from https://github.com/inner-product/http4s-native-image
  *
  * 1. "pt.davidafsilva.apple" % "jkeychain" uses JNI to interact with the osx keychain. This requires some special config
  *     to work with native-image.
  *
  *     - The classes that are used need to be enumerated in META-INF/native-image/com.elijordan/alfred-jenkins/jni-config.json
  *       This config we generated using the graal native image agent.
  *
  *     - The library includes the osxkeychain.so library, that is invoked using JNI. This library is loaded as a resource, so
  *       needs to be declared in META-INF/native-image/com.elijordan/alfred-jenkins/resource-config.json
  *
  * 2. scala.runtime.Statics$VM needs to be initialised at native-image build time, to avoid errors. See this issue for details
  *     - https://github.com/scala/bug/issues/11634
  *     - https://github.com/oracle/graal/issues/2019
  *
  *    However, when just including this class in --initialize-at-build-time native-image errors out indicating that several classes
  *    were initialized at build time, but should not have. So, we just initialize the whole scala.* package at build time.
  *
  * 3. Any classes that are interacted with using reflection need to be enumerated. In this application its just logback and
  *    slf4j see META-INF/native-image/com.elijordan/alfred-jenkins/reflect-config.json
  */
lazy val root = (project in file("."))
  .settings(
    name := "alfred-jenkins",
    graalVMNativeImageCommand := "/Users/elias.jordan/graalvm-ce-java11-20.1.0/Contents/Home/bin/native-image",
    graalVMNativeImageOptions := Seq(
      "--verbose",
      "-H:+TraceClassInitialization",
      "-H:+ReportExceptionStackTraces",
      "--no-fallback",
      "--allow-incomplete-classpath",
      "--initialize-at-build-time=scala",
      "--enable-http",
      "--enable-https"
    ),
    mainClass := Some("alfred.jenkins.AlfredJenkins"),
    libraryDependencies ++= Seq(
      "io.circe"              %% "circe-core"           % circeVersion,
      "io.circe"              %% "circe-generic"        % circeVersion,
      "io.circe"              %% "circe-generic-extras" % circeVersion,
      "pt.davidafsilva.apple" % "jkeychain"             % "1.0.0",
      "org.typelevel"         %% "cats-effect"          % "2.2.0-RC1",
      "org.http4s"            %% "http4s-blaze-client"  % "0.21.6",
      "org.http4s"            %% "http4s-circe"         % "0.21.6",
      "org.http4s"            %% "http4s-dsl"           % "0.21.6",
      "com.monovore"          %% "decline"              % "1.0.0",
      "io.chrisdavenport"     %% "log4cats-slf4j"       % "1.1.1",
      "ch.qos.logback"        % "logback-classic"       % "1.2.3"
    )
  )
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(GraalVMNativeImagePlugin)

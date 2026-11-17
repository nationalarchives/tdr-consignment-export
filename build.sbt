import Dependencies.*
import ReleaseTransformations.*
import sbt.Keys.dependencyOverrides

import java.io.FileWriter

ThisBuild / scalaVersion := "2.13.18"
ThisBuild / organization := "uk.gov.nationalarchives"
ThisBuild / organizationName := "The National Archives"

lazy val setLatestTagOutput = taskKey[Unit]("Sets a GitHub actions output for the latest tag")

setLatestTagOutput := {
  val fileWriter = new FileWriter(sys.env("GITHUB_OUTPUT"), true)
  fileWriter.write(s"latest-tag=${(ThisBuild / version).value}\n")
  fileWriter.close()
}

lazy val root = (project in file("."))
  .settings(
    releaseProcess := Seq[ReleaseStep](
      inquireVersions,
      setReleaseVersion,
      releaseStepTask(setLatestTagOutput),
      commitReleaseVersion,
      tagRelease,
      pushChanges,
      releaseStepTask(bagitExport / Universal / packageZipTarball),
      releaseStepTask(exportProject / Universal / packageZipTarball),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
  .aggregate(bagitExport, exportProject)


val commonSettings = Seq(
  libraryDependencies ++= Seq(
    s3Utils,
    catsEffect,
    decline,
    declineEffect,
    log4cats,
    log4catsSlf4j,
    mockitoScala % Test,
    mockitoScalaTest % Test,
    pureConfig,
    pureConfigCatsEffect,
    scalaTest % Test,
    slf4j,
    stepFunctionUtils
  ),
  (Test / fork) := true,
  (Test / envVars) := Map("AWS_ACCESS_KEY_ID" -> "test", "AWS_SECRET_ACCESS_KEY" -> "test",
    "AWS_REQUEST_CHECKSUM_CALCULATION" -> "when_required", "AWS_RESPONSE_CHECKSUM_CALCULATION" -> "when_required"),
  (Test / javaOptions) += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf",
  buildInfoKeys := Seq[BuildInfoKey](version),
  buildInfoPackage := "uk.gov.nationalarchives.consignmentexport",
)

lazy val exportProject = (project in file("export"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      awsRds,
      doobie,
      doobiePostgres,
      snsUtils,
      catsEffectTest % Test,
      testContainers % Test,
      testContainersPostgres % Test,
      postgres % Test,
      wiremock % Test,
      jsonpath % Test
    ),
    name := "tdr-export",
    (Universal / packageName) := "tdr-export"
  ).enablePlugins(JavaAppPackaging, UniversalPlugin, BuildInfoPlugin)

lazy val bagitExport = (project in file("bagit-export"))
  .settings(commonSettings)
  .settings(
    Test / parallelExecution := false,
    name := "tdr-consignment-export",
    libraryDependencies ++= Seq(
      authUtils,
      bagit,
      generatedGraphql,
      graphqlClient,
      keycloakCore,
      keycloakAdminClient,
      scalaCsv,
      slf4j,
      schemaConfig,
      tdrStatuses
    ),
    (Universal / packageName) := "tdr-consignment-export",
    dependencyOverrides += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
    (Test / javaOptions) += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"
  ).enablePlugins(JavaAppPackaging, UniversalPlugin, BuildInfoPlugin)

val nettyVersion = "4.2.20.Final"
val bouncyCastleVersion = "1.87"

ThisBuild / dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.21.8",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.21.8",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.21",
  "io.netty" % "netty-buffer" % nettyVersion,
  "io.netty" % "netty-codec" % nettyVersion,
  "io.netty" % "netty-codec-http" % nettyVersion,
  "io.netty" % "netty-codec-http2" % nettyVersion,
  "io.netty" % "netty-common" % nettyVersion,
  "io.netty" % "netty-handler" % nettyVersion,
  "io.netty" % "netty-resolver" % nettyVersion,
  "io.netty" % "netty-transport" % nettyVersion,
  "io.netty" % "netty-transport-classes-epoll" % nettyVersion,
  "io.netty" % "netty-transport-native-unix-common" % nettyVersion,
  "org.bouncycastle" % "bcprov-jdk18on" % bouncyCastleVersion,
  "org.bouncycastle" % "bcpkix-jdk18on" % bouncyCastleVersion,
  "org.bouncycastle" % "bcutil-jdk18on" % bouncyCastleVersion,
  "org.bouncycastle" % "bcpg-jdk18on"   % bouncyCastleVersion
)

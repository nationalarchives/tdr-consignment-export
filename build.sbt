import Dependencies._
import ReleaseTransformations._
import java.io.FileWriter

ThisBuild / scalaVersion := "2.13.13"
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
    name := "tdr-consignment-export",
    libraryDependencies ++= Seq(
      authUtils,
      s3Utils,
      stepFunctionUtils,
      bagit,
      catsEffect,
      decline,
      declineEffect,
      generatedGraphql,
      graphqlClient,
      keycloakCore,
      keycloakAdminClient,
      log4cats,
      log4catsSlf4j,
      mockitoScala % Test,
      mockitoScalaTest % Test,
      pureConfig,
      pureConfigCatsEffect,
      scalaCsv,
      scalaTest % Test,
      slf4j
    ),
    (Universal / packageName) := "tdr-consignment-export",
    (Test / fork) := true,
    (Test / envVars) := Map("AWS_ACCESS_KEY_ID" -> "test", "AWS_SECRET_ACCESS_KEY" -> "test"),
    (Test / javaOptions) += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf",
    releaseProcess := Seq[ReleaseStep](
      inquireVersions,
      setReleaseVersion,
      releaseStepTask(setLatestTagOutput),
      commitReleaseVersion,
      tagRelease,
      pushChanges,
      releaseStepTask(Universal / packageZipTarball),
      setNextVersion,
      commitNextVersion,
      pushChanges
    ),
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "uk.gov.nationalarchives.consignmentexport",
    dependencyOverrides += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.2",
    (Test / javaOptions) += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"
  ).enablePlugins(JavaAppPackaging, UniversalPlugin, BuildInfoPlugin)

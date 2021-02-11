import Dependencies._
import ReleaseTransformations._

import scala.sys.process._
import java.nio.file.{Files, Paths}
import java.io.File
import java.nio.charset.StandardCharsets

import scala.io.Source

ThisBuild / scalaVersion := "2.13.3"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val generateChangelogFile = taskKey[Unit]("Generates a changelog file from the last version")

generateChangelogFile := {
  val lastTag = "git describe --tags --abbrev=0".!!.replace("\n","")
  val gitLog = s"git log $lastTag..HEAD --oneline".!!
  val folderName = s"${baseDirectory.value}/notes"
  val fileName = s"${version.value}.markdown"
  val fullPath = s"$folderName/$fileName"
  new File(folderName).mkdirs()
  val file = new File(fullPath)
  if(!file.exists()) {
    new File(fullPath).createNewFile
    Files.write(Paths.get(fullPath), gitLog.getBytes(StandardCharsets.UTF_8))
  }
  s"git add $fullPath".!!
}

lazy val root = (project in file("."))
  .settings(
    name := "tdr-consignment-export",
    resolvers ++= Seq[Resolver](
      "TDR Releases" at "s3://tdr-releases-mgmt"
    ),
    libraryDependencies ++= Seq(
      authUtils,
      awsUtils,
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
      s3Mock,
      scalaCsv,
      scalaTest % Test,
      slf4j
    ),
    packageName in Universal := "tdr-consignment-export",
    fork in Test := true,
    javaOptions in Test += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf",
    ghreleaseRepoOrg := "nationalarchives",
    ghreleaseAssets := Seq(file(s"${(target in Universal).value}/${(packageName in Universal).value}.tgz")),
    releaseProcess := Seq[ReleaseStep](
      inquireVersions,
      setReleaseVersion,
      releaseStepTask(generateChangelogFile),
      commitReleaseVersion,
      tagRelease,
      pushChanges,
      releaseStepTask(packageZipTarball in Universal),
      releaseStepInputTask(githubRelease),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  ).enablePlugins(JavaAppPackaging, UniversalPlugin)
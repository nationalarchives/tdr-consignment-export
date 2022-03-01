import sbt._

object Dependencies {
  private val githubPureConfigVersion = "0.14.0"
  private val keycloakVersion = "16.1.0"
  private val log4CatsVersion = "1.1.1"
  private val mockitoScalaVersion = "1.16.0"
  private val monovoreDeclineVersion = "1.3.0"

  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.30"
  lazy val awsUtils =  "uk.gov.nationalarchives.aws.utils" %% "tdr-aws-utils" % "0.1.18"
  lazy val bagit = "gov.loc" % "bagit" % "5.2.0"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.2.0"
  lazy val decline = "com.monovore" %% "decline" % monovoreDeclineVersion
  lazy val declineEffect = "com.monovore" %% "decline-effect" % monovoreDeclineVersion
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.207-SNAPSHOT"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.15"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.2"
  lazy val scalaCsv = "com.github.tototoshi" %% "scala-csv" % "1.3.6"
  lazy val log4cats = "io.chrisdavenport" %% "log4cats-core" % log4CatsVersion
  lazy val log4catsSlf4j = "io.chrisdavenport" %% "log4cats-slf4j" % log4CatsVersion
  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % githubPureConfigVersion
  lazy val pureConfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % githubPureConfigVersion
  lazy val s3Mock = "io.findify" %% "s3mock" % "0.2.6"
  lazy val slf4j = "org.slf4j" % "slf4j-simple" % "1.7.30"
  lazy val mockitoScala = "org.mockito" %% "mockito-scala" % mockitoScalaVersion
  lazy val mockitoScalaTest = "org.mockito" %% "mockito-scala-scalatest" % mockitoScalaVersion
  lazy val keycloakCore = "org.keycloak" % "keycloak-core" % keycloakVersion
  lazy val keycloakAdminClient =  "org.keycloak" % "keycloak-admin-client" % keycloakVersion
}

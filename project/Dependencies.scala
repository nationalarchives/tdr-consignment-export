import sbt._

object Dependencies {
  private val githubPureConfigVersion = "0.17.6"
  private val keycloakVersion = "24.0.4"
  private val log4CatsVersion = "2.7.0"
  private val mockitoScalaVersion = "1.17.31"
  private val monovoreDeclineVersion = "2.4.1"
  private val awsUtilsVersion = "0.1.172"
  private val doobieVersion = "1.0.0-RC5"
  private val testContainersVersion = "0.41.2"

  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.198"
  lazy val awsRds = "software.amazon.awssdk" % "rds" % "2.25.35"
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.376"
  lazy val s3Utils = "uk.gov.nationalarchives" %% "s3-utils" % awsUtilsVersion
  lazy val stepFunctionUtils = "uk.gov.nationalarchives" %% "stepfunction-utils" % awsUtilsVersion
  lazy val snsUtils = "uk.gov.nationalarchives" %% "sns-utils" % awsUtilsVersion
  lazy val bagit = "gov.loc" % "bagit" % "5.2.0"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.4"
  lazy val decline = "com.monovore" %% "decline" % monovoreDeclineVersion
  lazy val declineEffect = "com.monovore" %% "decline-effect" % monovoreDeclineVersion
  lazy val doobie = "org.tpolecat" %% "doobie-core" % doobieVersion
  lazy val doobiePostgres = "org.tpolecat" %% "doobie-postgres"  % doobieVersion
  lazy val postgres = "org.postgresql" % "postgresql" % "42.7.3"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.159"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18"
  lazy val scalaCsv = "com.github.tototoshi" %% "scala-csv" % "1.3.10"
  lazy val log4cats = "org.typelevel" %% "log4cats-core" % log4CatsVersion
  lazy val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % log4CatsVersion
  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % githubPureConfigVersion
  lazy val pureConfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % githubPureConfigVersion
  lazy val slf4j = "org.slf4j" % "slf4j-simple" % "2.0.13"
  lazy val mockitoScala = "org.mockito" %% "mockito-scala" % mockitoScalaVersion
  lazy val mockitoScalaTest = "org.mockito" %% "mockito-scala-scalatest" % mockitoScalaVersion
  lazy val keycloakCore = "org.keycloak" % "keycloak-core" % keycloakVersion
  lazy val keycloakAdminClient = "org.keycloak" % "keycloak-admin-client" % keycloakVersion
  lazy val testContainers = "com.dimafeng" %% "testcontainers-scala-scalatest" % testContainersVersion
  lazy val testContainersPostgres = "com.dimafeng" %% "testcontainers-scala-postgresql" % testContainersVersion
  lazy val wiremock = "org.wiremock" % "wiremock" % "3.5.4"
}

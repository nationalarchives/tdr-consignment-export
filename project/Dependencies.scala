import sbt._

object Dependencies {
  private val githubPureConfigVersion = "0.17.8"
  private val keycloakVersion = "26.1.4"
  private val log4CatsVersion = "2.7.0"
  private val mockitoScalaVersion = "1.17.37"
  private val monovoreDeclineVersion = "2.5.0"
  private val awsUtilsVersion = "0.1.236"
  private val doobieVersion = "1.0.0-RC8"
  private val testContainersVersion = "0.43.0"

  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.235"
  lazy val awsRds = "software.amazon.awssdk" % "rds" % "2.26.27"
  lazy val generatedGraphql = "uk.gov.nationalarchives" %% "tdr-generated-graphql" % "0.0.403"
  lazy val s3Utils = "uk.gov.nationalarchives" %% "s3-utils" % awsUtilsVersion
  lazy val stepFunctionUtils = "uk.gov.nationalarchives" %% "stepfunction-utils" % awsUtilsVersion
  lazy val snsUtils = "uk.gov.nationalarchives" %% "sns-utils" % awsUtilsVersion
  lazy val bagit = "gov.loc" % "bagit" % "5.2.0"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.7"
  lazy val catsEffectTest = "org.typelevel" %% "cats-effect-testkit" % "3.5.7"
  lazy val decline = "com.monovore" %% "decline" % monovoreDeclineVersion
  lazy val declineEffect = "com.monovore" %% "decline-effect" % monovoreDeclineVersion
  lazy val doobie = "org.tpolecat" %% "doobie-core" % doobieVersion
  lazy val doobiePostgres = "org.tpolecat" %% "doobie-postgres"  % doobieVersion
  lazy val postgres = "org.postgresql" % "postgresql" % "42.7.5"
  lazy val graphqlClient = "uk.gov.nationalarchives" %% "tdr-graphql-client" % "0.0.217"
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19"
  lazy val scalaCsv = "com.github.tototoshi" %% "scala-csv" % "2.0.0"
  lazy val log4cats = "org.typelevel" %% "log4cats-core" % log4CatsVersion
  lazy val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % log4CatsVersion
  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % githubPureConfigVersion
  lazy val pureConfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % githubPureConfigVersion
  lazy val slf4j = "org.slf4j" % "slf4j-simple" % "2.0.17"
  lazy val mockitoScala = "org.mockito" %% "mockito-scala" % mockitoScalaVersion
  lazy val mockitoScalaTest = "org.mockito" %% "mockito-scala-scalatest" % mockitoScalaVersion
  lazy val keycloakCore = "org.keycloak" % "keycloak-core" % keycloakVersion
  lazy val keycloakAdminClient = "org.keycloak" % "keycloak-admin-client" % "26.0.4"
  lazy val testContainers = "com.dimafeng" %% "testcontainers-scala-scalatest" % testContainersVersion
  lazy val testContainersPostgres = "com.dimafeng" %% "testcontainers-scala-postgresql" % testContainersVersion
  lazy val wiremock = "org.wiremock" % "wiremock" % "3.12.1"
  lazy val jsonpath = "com.jayway.jsonpath" % "json-path-assert" % "2.9.0"
}

package uk.gov.nationalarchives

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.dimafeng.testcontainers.{ContainerDef, PostgreSQLContainer}
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.{ServeEvent, StubMapping}
import doobie.Transactor
import doobie.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import org.testcontainers.utility.DockerImageName

import java.util.UUID
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsJava}

class TestUtils extends AnyFlatSpec with TestContainerForAll with BeforeAndAfterAll with EitherValues {

  override val containerDef: ContainerDef = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName
      .parse("tests")
      .asCompatibleSubstituteFor("postgres"),
    databaseName = "consignmentapi",
    username = "tdr",
    password = "password"
  )

  val sfnServer = new WireMockServer(9009)
  val s3Server = new WireMockServer(9010)


  val userId = "52be6447-eb7d-4894-ae71-444d3ecf5436"
  val dateTime = "2024-04-29 12:21:37.553+00"

  override def beforeAll(): Unit = {
    sfnServer.start()
    s3Server.start()
    sfnServer.resetAll()
    s3Server.resetAll()
    sfnServer.stubFor(
      post(urlEqualTo("/"))
        .willReturn(ok())
    )

  }

  def stubPutRequest(fileId: UUID): StubMapping = {
    s3Server.stubFor(
      put(urlEqualTo(s"/$fileId.metadata"))
        .withHost(equalTo("output.localhost"))
        .willReturn(ok())
    )
  }

  def stubCopyRequest(consignmentId: UUID, fileId: UUID): Unit = {
    val sourceName = s"/$consignmentId/$fileId"
    val destinationName = s"/$fileId"
    val response =
      <CopyObjectResult>
        <LastModified>2023-08-29T17:50:30.000Z</LastModified>
        <ETag>"9b2cf535f27731c974343645a3985328"</ETag>
      </CopyObjectResult>
    s3Server.stubFor(
      head(urlEqualTo(destinationName))
        .willReturn(ok().withHeader("Content-Length", "1"))
    )
    s3Server.stubFor(
      head(urlEqualTo(sourceName))
        .willReturn(ok().withHeader("Content-Length", "1"))
    )
    s3Server.stubFor(
      put(urlEqualTo(destinationName))
        .withHost(equalTo("output.localhost"))
        .withHeader("x-amz-copy-source", equalTo(s"clean$sourceName"))
        .willReturn(okXml(response.toString()))
    )
  }

  def stubS3Get(consignmentId: UUID, fileId: UUID): StubMapping = {
    val params = Map("list-type" -> equalTo("2"), "prefix" -> equalTo(s"$consignmentId/")).asJava
    val response = <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
      <Contents>
        <Key>{consignmentId}/{fileId}</Key>
        <LastModified>2009-10-12T17:50:30.000Z</LastModified>
        <ETag>"fba9dede5f27731c9771645a39863328"</ETag>
        <Size>1</Size>
      </Contents>
    </ListBucketResult>
    s3Server.stubFor(
      get(urlPathEqualTo("/"))
        .withHost(equalTo("clean.localhost"))
        .withQueryParams(params)
        .willReturn(okXml(response.toString))
    )
  }

  def stubEmptyS3Get(consignmentId: UUID): StubMapping = {
    val params = Map("list-type" -> equalTo("2"), "prefix" -> equalTo(s"$consignmentId/")).asJava
    val response = <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
    </ListBucketResult>
    s3Server.stubFor(
      get(urlPathEqualTo("/"))
        .withHost(equalTo("clean.localhost"))
        .withQueryParams(params)
        .willReturn(okXml(response.toString))
    )
  }

  def stubExternalServices(mappedPort: Int): (UUID, UUID, String) = {
    System.setProperty("db.port", mappedPort.toString)
    val consignmentId = UUID.randomUUID()
    val fileId = UUID.randomUUID()
    val consignmentReference = seedDatabase(mappedPort, consignmentId.toString)
    stubS3Get(consignmentId, fileId)
    stubCopyRequest(consignmentId, fileId)
    stubPutRequest(fileId)
    (consignmentId, fileId, consignmentReference)
  }

  def getRequestBody(predicate: ServeEvent => Boolean): String =
    s3Server.getAllServeEvents.asScala
      .find(predicate)
      .map(_.getRequest.getBodyAsString.trim)
      .getOrElse("")
      .split("\n")
      .tail
      .head
      .trim

  def addFileMetadata(consignmentId: UUID, fileId: UUID, port: Int): Unit = {
    val jdbcUrl = s"jdbc:postgresql://localhost:$port/consignmentapi"
    val transactor = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = jdbcUrl,
      user = "tdr",
      password = "password",
      logHandler = None
    )
    for {
      _ <- sql""" INSERT INTO "FileProperty" ("Name") VALUES ('FileMetadataTest') """.update.run.transact(transactor)
      _ <-
        sql""" INSERT INTO "File" ("FileId", "ConsignmentId", "UserId", "Datetime")
             VALUES (CAST(${fileId.toString} AS UUID), CAST(${consignmentId.toString} AS UUID), CAST($userId AS UUID), CAST($dateTime AS TIMESTAMP)) """.update.run
          .transact(transactor)
      _ <-
        sql""" INSERT INTO "FileMetadata" ("MetadataId", "FileId", "Value", "PropertyName", "UserId")
             VALUES (CAST(${UUID.randomUUID.toString} AS UUID), CAST(${fileId.toString} AS UUID),'TestValue', 'FileMetadataTest', CAST($userId AS UUID)) """.update.run
          .transact(transactor)
    } yield ()
  }.unsafeRunSync()

  def addConsignmentMetadata(consignmentId: UUID, port: Int): Unit = {
    val jdbcUrl = s"jdbc:postgresql://localhost:$port/consignmentapi"
    val transactor = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = jdbcUrl,
      user = "tdr",
      password = "password",
      logHandler = None
    )
    for {
      _ <- sql""" INSERT INTO "ConsignmentProperty" ("Name") VALUES ('ConsignmentMetadataTest') """.update.run.transact(transactor)
      _ <-
        sql""" INSERT INTO "ConsignmentMetadata" ("MetadataId", "ConsignmentId", "Value", "PropertyName", "UserId")
             VALUES (CAST(${UUID.randomUUID.toString} AS UUID), CAST(${consignmentId.toString} AS UUID),'TestValue', 'ConsignmentMetadataTest', CAST($userId AS UUID)) """.update.run
          .transact(transactor)
    } yield ()
  }.unsafeRunSync()

  def seedDatabase(port: Int, consignmentId: String): String = {
    val jdbcUrl = s"jdbc:postgresql://localhost:$port/consignmentapi"
    val bodyId: String = UUID.randomUUID().toString
    val consignmentRef = UUID.randomUUID().toString.replaceAll("-", "")
    val transactor = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = jdbcUrl,
      user = "tdr",
      password = "password",
      logHandler = None
    )
    (for {
      sequence <- sql"""select nextval('consignment_sequence_id')""".query[Int].unique.transact(transactor)
      _ <- sql""" INSERT INTO "Body" ("BodyId", "Name", "TdrCode") VALUES (CAST($bodyId AS UUID), 'Test', 'Test') """.update.run.transact(transactor)
      _ <- sql""" INSERT INTO "Consignment" ("ConsignmentId", "UserId", "Datetime", "ConsignmentSequence", "ConsignmentReference", "ConsignmentType", "BodyId")
         VALUES (CAST($consignmentId AS UUID), CAST($userId AS UUID), CAST($dateTime AS TIMESTAMP), $sequence, $consignmentRef, 'standard', CAST($bodyId AS UUID)) """.update.run
        .transact(transactor)
    } yield consignmentRef).unsafeRunSync()
  }

}

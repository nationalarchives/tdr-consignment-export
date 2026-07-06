package uk.gov.nationalarchives.`export`

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.toTraverseOps
import com.dimafeng.testcontainers.scalatest.TestContainerForAll
import com.dimafeng.testcontainers.{ContainerDef, PostgreSQLContainer}
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.{ServeEvent, StubMapping}
import org.typelevel.doobie.Transactor
import org.typelevel.doobie.implicits._
import org.typelevel.doobie.util.transactor.Transactor.Aux
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.{BeforeAndAfterAll, EitherValues}
import org.testcontainers.utility.DockerImageName
import MetadataUtils.{AssetId, Metadata}
import uk.gov.nationalarchives.`export`.RecordIdHandler.RecordIds

import java.util.UUID
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsJava}

class TestUtils extends AnyFlatSpec with TestContainerForAll with BeforeAndAfterAll with EitherValues {

  override val containerDef: PostgreSQLContainer.Def = PostgreSQLContainer.Def(
    dockerImageName = DockerImageName
      .parse("tests")
      .asCompatibleSubstituteFor("postgres"),
    databaseName = "consignmentapi",
    username = "tdr",
    password = "password"
  )

  override def afterContainersStart(containers: PostgreSQLContainer): Unit = {
    val transactor = createTransactor(containers.mappedPort(5432))
    List("PropertyName","OriginalFilepath","ClientSideOriginalFilepath","FileReference","AssetId").map { propertyName =>
      sql""" INSERT INTO "FileProperty" ("Name") VALUES ($propertyName) """.update.run.transact(transactor)
    }.sequence.unsafeRunSync()

    super.afterContainersStart(containers)
  }

  val sfnServer = new WireMockServer(9009)
  val s3Server = new WireMockServer(9010)
  val snsServer = new WireMockServer(9011)
  val userId = "52be6447-eb7d-4894-ae71-444d3ecf5436"
  val dateTime = "2024-04-29 12:21:37.553+00"

  override def beforeAll(): Unit = {
    sfnServer.start()
    s3Server.start()
    snsServer.start()
    sfnServer.resetAll()
    s3Server.resetAll()
    snsServer.resetAll()
    sfnServer.stubFor(
      post(urlEqualTo("/"))
        .willReturn(ok())
    )
    snsServer.stubFor(
      post(urlEqualTo("/"))
        .willReturn(ok())
    )
  }

  def stubPutRequest(recordIds: List[RecordIds]): Unit = {
    recordIds.map { ids =>
      s3Server.stubFor(
        put(urlEqualTo(s"/${ids.assetId}.metadata"))
          .withHost(equalTo("output.localhost"))
          .willReturn(ok())
      )
    }
  }

  def stubCopyRequest(consignmentId: UUID, recordIds: List[RecordIds]): Unit = {
    recordIds.map { ids =>
      val sourceName = s"/$consignmentId/${ids.fileId}"
      val destinationName = s"/${ids.assetId}"
      val response =
        <CopyObjectResult>
          <LastModified>2023-08-29T17:50:30.000Z</LastModified>
          <ETag>"9b2cf535f27731c974343645a3985328"</ETag>
        </CopyObjectResult>
      s3Server.stubFor(
        head(urlMatching(s"$destinationName/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
          .willReturn(ok().withHeader("Content-Length", "1"))
      )
      s3Server.stubFor(
        head(urlEqualTo(sourceName))
          .willReturn(ok().withHeader("Content-Length", "1"))
      )
      s3Server.stubFor(
        put(urlMatching(s"$destinationName/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
          .withHost(equalTo("output.localhost"))
          .withHeader("x-amz-copy-source", equalTo(s"clean$sourceName"))
          .willReturn(okXml(response.toString()))
      )
    }
  }

  def stubS3Get(consignmentId: UUID, recordIds: List[RecordIds]): StubMapping = {
    val params = Map("list-type" -> equalTo("2"), "prefix" -> equalTo(s"$consignmentId/")).asJava
    val response = <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
      {recordIds.map(ids =>
        <Contents>
          <Key>{consignmentId}/{ids.fileId}</Key>
          <LastModified>2009-10-12T17:50:30.000Z</LastModified>
          <ETag>"fba9dede5f27731c9771645a39863328"</ETag>
          <Size>1</Size>
        </Contents>
      )}
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

  def stubExternalServices(mappedPort: Int, numberOfRecords: Int = 1, shouldAddMetadata: Boolean = true, seriesName: String = UUID.randomUUID().toString): (UUID, List[RecordIds], String) = {
    System.setProperty("db.port", mappedPort.toString)
    val consignmentId = UUID.randomUUID()
    val fileIds = (1 to numberOfRecords).map(_ => UUID.randomUUID()).toList.sorted
    val recordIds = fileIds.map(id => RecordIds(UUID.randomUUID(), id))
    val consignmentReference = seedDatabase(mappedPort, consignmentId.toString, recordIds, seriesName = seriesName)
    stubS3Get(consignmentId, recordIds)
    stubCopyRequest(consignmentId, recordIds)
    stubPutRequest(recordIds)
    if(shouldAddMetadata) {
      addFileMetadata(recordIds.flatMap(ids =>
        List(
          Metadata(ids.fileId, AssetId.id, ids.assetId.toString),
          Metadata(ids.fileId, "PropertyName", "Value")
        )
      ), mappedPort)
    }
    (consignmentId, recordIds, consignmentReference)
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

  def addFFIDMetadata(fileId: UUID, port: Int, hasNullMatchValues: Boolean = false): Unit = {
    val jdbcUrl = s"jdbc:postgresql://localhost:$port/consignmentapi"
    val ffidMetadataId = UUID.randomUUID.toString
    val transactor = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = jdbcUrl,
      user = "tdr",
      password = "password",
      logHandler = None
    )
    val matchesSql = if(hasNullMatchValues) {
      sql""" INSERT INTO "FFIDMetadataMatches" ("FFIDMetadataId", "IdentificationBasis", "ExtensionMismatch") VALUES
          (CAST($ffidMetadataId AS UUID), 'IdentificationBasis', TRUE)"""
    } else {
      sql""" INSERT INTO "FFIDMetadataMatches" ("FFIDMetadataId", "Extension", "IdentificationBasis", "PUID", "ExtensionMismatch", "FormatName") VALUES
          (CAST($ffidMetadataId AS UUID), 'Extension', 'IdentificationBasis', 'PUID', TRUE, 'FormatName')"""
    }

    for {
      _ <-
        sql""" INSERT INTO "FFIDMetadata" ("FFIDMetadataId", "FileId", "Software", "SoftwareVersion", "Datetime", "BinarySignatureFileVersion", "ContainerSignatureFileVersion", "Method")
             VALUES (CAST($ffidMetadataId AS UUID), CAST(${fileId.toString} AS UUID),'Software', 'SoftwareVersion', CAST($dateTime AS TIMESTAMP), 'BinarySignatureFileVersion', 'ContainerSignatureFileVersion', 'Method') """.update.run
          .transact(transactor)
      _ <-
        matchesSql.update.run.transact(transactor)

    } yield ()
  }.unsafeRunSync()

  def addFileMetadata(metadataEntries: List[Metadata], port: Int, includeFFIDMetadata: Boolean = true): Unit = {
    val transactor = createTransactor(port)
    metadataEntries.map { metadata =>
      for {
        _ <- sql""" INSERT INTO "FileMetadata" ("MetadataId", "FileId", "Value", "PropertyName", "UserId")
             VALUES (CAST(${UUID.randomUUID.toString} AS UUID), CAST(${metadata.id.toString} AS UUID),${metadata.value}, ${metadata.propertyName}, CAST($userId AS UUID)) """.update.run
          .transact(transactor).flatMap(_ => IO.unit)
      } yield ()
    }.sequence
  }.unsafeRunSync()

  private def createTransactor(port: Int) = {
    val jdbcUrl = s"jdbc:postgresql://localhost:$port/consignmentapi"
    val transactor = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = jdbcUrl,
      user = "tdr",
      password = "password",
      logHandler = None
    )
    transactor
  }

  def addConsignmentMetadata(consignmentId: UUID, port: Int): Unit = {
    val transactor = createTransactor(port)
    for {
      _ <- sql""" INSERT INTO "ConsignmentProperty" ("Name") VALUES ('ConsignmentMetadataTest') """.update.run.transact(transactor)
      _ <-
        sql""" INSERT INTO "ConsignmentMetadata" ("MetadataId", "ConsignmentId", "Value", "PropertyName", "UserId")
             VALUES (CAST(${UUID.randomUUID.toString} AS UUID), CAST(${consignmentId.toString} AS UUID),'TestValue', 'ConsignmentMetadataTest', CAST($userId AS UUID)) """.update.run
          .transact(transactor)
    } yield ()
  }.unsafeRunSync()

  def seedDatabase(port: Int, consignmentId: String, recordIds: List[RecordIds], seriesName: String): String = {
    val jdbcUrl = s"jdbc:postgresql://localhost:$port/consignmentapi"
    val bodyId: String = UUID.randomUUID().toString
    val seriesId: String = UUID.randomUUID().toString
    val bodyCode = UUID.randomUUID().toString
    val consignmentRef = UUID.randomUUID().toString.replaceAll("-", "")
    val metadataSchemaLibraryVersion = "Schema-Library-Version-v0.1"
    val transactor = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = jdbcUrl,
      user = "tdr",
      password = "password",
      logHandler = None
    )
    (for {
      sequence <- sql"""select nextval('consignment_sequence_id')""".query[Int].unique.transact(transactor)
      _ <- sql""" INSERT INTO "Body" ("BodyId", "Name", "TdrCode") VALUES (CAST($bodyId AS UUID), 'Test', $bodyCode) """.update.run.transact(transactor)
      _ <- sql"""INSERT INTO "Series" ("SeriesId", "BodyId", "Name", "Code") VALUES (CAST($seriesId AS UUID), CAST($bodyId AS UUID), $seriesName, $seriesName) """.update.run.transact(
        transactor
      )
      _ <- sql""" INSERT INTO "Consignment" ("ConsignmentId", "UserId", "Datetime", "ConsignmentSequence", "ConsignmentReference", "ConsignmentType", "BodyId", "SeriesId", "TransferInitiatedDatetime", "MetadataSchemaLibraryVersion")
         VALUES (CAST($consignmentId AS UUID), CAST($userId AS UUID), CAST($dateTime AS TIMESTAMP), $sequence, $consignmentRef, 'standard', CAST($bodyId AS UUID), CAST($seriesId AS UUID), '2024-08-29 00:00:00', $metadataSchemaLibraryVersion)""".update.run
        .transact(transactor)
      _ <- recordIds.map { ids =>
        val assetId = ids.assetId.toString
        val fileId = ids.fileId.toString

        sql""" INSERT INTO "File" ("FileId", "ConsignmentId", "UserId", "Datetime", "AssetId")
             VALUES (CAST($fileId AS UUID), CAST($consignmentId AS UUID), CAST($userId AS UUID), CAST($dateTime AS TIMESTAMP), CAST($assetId AS UUID)) """.update.run
          .transact(transactor)
      }.sequence
    } yield consignmentRef).unsafeRunSync()
  }
}

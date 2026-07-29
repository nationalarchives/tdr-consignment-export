package uk.gov.nationalarchives.`export`

import cats.effect.unsafe.implicits.global
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.github.tomakehurst.wiremock.http.RequestMethod
import com.jayway.jsonpath.JsonPath
import io.circe.generic.auto._
import io.circe.parser.decode
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.`export`.MetadataUtils.Metadata
import uk.gov.nationalarchives.`export`.S3Utils.FileOutput

import java.net.URI
import java.util.UUID
import scala.jdk.CollectionConverters.ListHasAsScala

class MainTest extends TestUtils {

  "run" should "use the tdr file id as asset id where no asset id persisted" in withContainers {
    container: PostgreSQLContainer =>
      val mappedPort = container.mappedPort(5432)
      val seriesName: String = UUID.randomUUID().toString
      val (consignmentId, testRecordIds, consignmentReference) = stubExternalServices(mappedPort, seriesName = seriesName, persistedAssetIds = false)
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

      val metadataKeyId = testRecordIds.head.fileId
      val serveEvents = s3Server.getAllServeEvents.asScala
      val metadataFileWriteBody = serveEvents
        .find(req => req.getRequest.getUrl == s"/$metadataKeyId.metadata" && req.getRequest.getMethod == RequestMethod.PUT)
        .map(_.getRequest.getBodyAsString)
        .getOrElse("")
      val jsonReturned = metadataFileWriteBody.split("\n").tail.head.trim

      JsonPath.read[Int](jsonReturned, "$.[0].size()") shouldEqual 9
      JsonPath.read[java.util.List[Any]](jsonReturned, "$.[0].FFID").asScala.toArray shouldEqual Array.empty[Any]
      JsonPath.read[String](jsonReturned, "$.[0].PropertyName") shouldEqual "Value"
      JsonPath.read[String](jsonReturned, "$.[0].UserId") shouldEqual userId
      JsonPath.read[String](jsonReturned, "$.[0].fileId") should not equal testRecordIds.head.fileId.toString
      JsonPath.read[String](jsonReturned, "$.[0].Series") shouldEqual seriesName
      JsonPath.read[String](jsonReturned, "$.[0].TransferInitiatedDatetime") shouldEqual "2024-08-29 00:00:00"
      JsonPath.read[String](jsonReturned, "$.[0].ConsignmentReference") shouldEqual consignmentReference
      JsonPath.read[String](jsonReturned, "$.[0].TransferringBody") shouldEqual "Test"
      JsonPath.read[String](jsonReturned, "$.[0].MetadataSchemaLibraryVersion") shouldEqual "Schema-Library-Version-v0.1"
  }

  "run" should "copy the files to the output bucket" in withContainers {
    container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, testRecordIds, _) = stubExternalServices(mappedPort)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val serveEvents = s3Server.getAllServeEvents.asScala
    val copyCount = serveEvents
      .count(req => req.getRequest.getMethod == RequestMethod.PUT && req.getRequest.getHeader("x-amz-copy-source") == s"clean/$consignmentId/${testRecordIds.head.fileId}")
    copyCount should equal(1)
  }

  "run" should "send a message to the SNS topic with the id of the file and the bucket name" in withContainers {
    container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, testRecordIds, _) = stubExternalServices(mappedPort)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val snsMessage = snsServer.getAllServeEvents.asScala.head.getRequest.getFormParameters.get("Message").values().get(0)
    val fileOutput = decode[FileOutput](snsMessage).value
    fileOutput.bucket should equal("output")
    fileOutput.assetId should equal(testRecordIds.head.assetId)
    fileOutput.fileId should equal(testRecordIds.head.fileId)
  }

  "run" should "send a message to the SNS topic with the metadata location" in withContainers {
    container: PostgreSQLContainer =>
      val mappedPort = container.mappedPort(5432)
      val (consignmentId, testRecordIds, _) = stubExternalServices(mappedPort)
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

      val snsMessage = snsServer.getAllServeEvents.asScala.head.getRequest.getFormParameters.get("Message").values().get(0)
      val fileOutput = decode[FileOutput](snsMessage).value
      fileOutput.metadataLocation should equal(URI.create(s"s3://output/${testRecordIds.head.assetId}.metadata"))
  }

  "run" should "send a message to the SNS topic with the id of the file being a random id when no asset id persisted" in withContainers {
    container: PostgreSQLContainer =>
      val mappedPort = container.mappedPort(5432)
      val (consignmentId, testRecordIds, _) = stubExternalServices(mappedPort, persistedAssetIds = false)
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

      val snsMessage = snsServer.getAllServeEvents.asScala.head.getRequest.getFormParameters.get("Message").values().get(0)
      val fileOutput = decode[FileOutput](snsMessage).value
      fileOutput.bucket should equal("output")
      fileOutput.assetId should equal(testRecordIds.head.fileId)
      fileOutput.fileId should not equal testRecordIds.head.fileId
      fileOutput.fileId should not equal testRecordIds.head.assetId
  }

  "run" should "not send a message to the SNS topic for a 'mock' series" in withContainers {
    container: PostgreSQLContainer =>
      val mappedPort = container.mappedPort(5432)
      val (consignmentId, testRecordIds, _) = stubExternalServices(mappedPort, seriesName = "MOCK123")
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

      val serveEventRequestBodies  = snsServer.getAllServeEvents.asScala.toList.map(_.getRequest.getBodyAsString)

      testRecordIds.foreach(id => {
        serveEventRequestBodies.contains(id.assetId) should be(false)
      })
  }

  "run" should "write consignment and file metadata to metadata json file" in withContainers {
    container: PostgreSQLContainer =>
      val mappedPort = container.mappedPort(5432)
      val seriesName: String = UUID.randomUUID().toString
      val (consignmentId, testRecordIds, consignmentReference) = stubExternalServices(mappedPort, seriesName = seriesName)
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

      val serveEvents = s3Server.getAllServeEvents.asScala
      val metadataFileWriteBody = serveEvents
        .find(req => req.getRequest.getUrl == s"/${testRecordIds.head.assetId}.metadata" && req.getRequest.getMethod == RequestMethod.PUT)
        .map(_.getRequest.getBodyAsString)
        .getOrElse("")
      val jsonReturned = metadataFileWriteBody.split("\n").tail.head.trim

      JsonPath.read[Int](jsonReturned, "$.[0].size()") shouldEqual 10
      JsonPath.read[java.util.List[Any]](jsonReturned, "$.[0].FFID").asScala.toArray shouldEqual Array.empty[Any]
      JsonPath.read[String](jsonReturned, "$.[0].PropertyName") shouldEqual "Value"
      JsonPath.read[String](jsonReturned, "$.[0].UserId") shouldEqual userId
      JsonPath.read[String](jsonReturned, "$.[0].AssetId") shouldEqual testRecordIds.head.assetId.toString
      JsonPath.read[String](jsonReturned, "$.[0].fileId") shouldEqual testRecordIds.head.fileId.toString
      JsonPath.read[String](jsonReturned, "$.[0].Series") shouldEqual seriesName
      JsonPath.read[String](jsonReturned, "$.[0].TransferInitiatedDatetime") shouldEqual "2024-08-29 00:00:00"
      JsonPath.read[String](jsonReturned, "$.[0].ConsignmentReference") shouldEqual consignmentReference
      JsonPath.read[String](jsonReturned, "$.[0].TransferringBody") shouldEqual "Test"
      JsonPath.read[String](jsonReturned, "$.[0].MetadataSchemaLibraryVersion") shouldEqual "Schema-Library-Version-v0.1"
  }

  "run" should "write the FFID metadata where it exists" in withContainers {
    container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, testRecordIds, _) = stubExternalServices(mappedPort)
    testRecordIds.foreach(recordIds => addFFIDMetadata(recordIds.fileId, mappedPort))
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val metadataFileWriteBody = getRequestBody(req => req.getRequest.getUrl == s"/${testRecordIds.head.assetId}.metadata" && req.getRequest.getMethod == RequestMethod.PUT)

    JsonPath.read[Int](metadataFileWriteBody, "$.[0].FFID[0].size()") shouldEqual 5
    JsonPath.read[String](metadataFileWriteBody, "$.[0].FFID[0].extension") shouldEqual "Extension"
    JsonPath.read[String](metadataFileWriteBody, "$.[0].FFID[0].identificationBasis") shouldEqual "IdentificationBasis"
    JsonPath.read[String](metadataFileWriteBody, "$.[0].FFID[0].puid") shouldEqual "PUID"
    JsonPath.read[Boolean](metadataFileWriteBody, "$.[0].FFID[0].extensionMismatch") shouldEqual true
    JsonPath.read[String](metadataFileWriteBody, "$.[0].FFID[0].formatName") shouldEqual "FormatName"
  }

 "run" should "return empty values if there are no FFID matches" in withContainers {
    container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, testRecordIds, _) = stubExternalServices(mappedPort)
    testRecordIds.foreach(recordIds => addFFIDMetadata(recordIds.fileId, mappedPort, hasNullMatchValues = true))
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val metadataFileWriteBody = getRequestBody(req => req.getRequest.getUrl == s"/${testRecordIds.head.assetId}.metadata" && req.getRequest.getMethod == RequestMethod.PUT)

    JsonPath.read[Int](metadataFileWriteBody, "$.[0].FFID[0].size()")  shouldEqual 5
    JsonPath.read[Option[String]](metadataFileWriteBody, "$.[0].FFID[0].extension") shouldEqual null
    JsonPath.read[String](metadataFileWriteBody, "$.[0].FFID[0].identificationBasis") shouldEqual "IdentificationBasis"
    JsonPath.read[Option[String]](metadataFileWriteBody, "$.[0].FFID[0].puid") shouldEqual null
    JsonPath.read[Boolean](metadataFileWriteBody,"$.[0].FFID[0].extensionMismatch") shouldEqual true
    JsonPath.read[Option[String]](metadataFileWriteBody, "$.[0].FFID[0].formatName") shouldEqual null
  }

  "run" should "add an OriginalFileReference field if this is a redacted record" in withContainers {
    container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, testRecordIds, _) = stubExternalServices(mappedPort, 2)
    val redactedMetadata = List(
      Metadata(testRecordIds.head.fileId, "OriginalFilepath", "/a/test/path"),
      Metadata(testRecordIds.last.fileId, "ClientSideOriginalFilepath", "/a/test/path"),
      Metadata(testRecordIds.last.fileId, "FileReference", "Z12345")
    )
    addFileMetadata(redactedMetadata, mappedPort, includeFFIDMetadata = false)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val metadataFileWriteBody = getRequestBody(req => req.getRequest.getUrl == s"/${testRecordIds.head.assetId}.metadata" && req.getRequest.getMethod == RequestMethod.PUT)

    JsonPath.read[java.util.List[Any]](metadataFileWriteBody, "$.[0].FFID").asScala.toArray shouldEqual Array.empty[Any]
    JsonPath.read[String](metadataFileWriteBody, "$.[0].OriginalFilepath") shouldEqual "/a/test/path"
  }

  "run" should "return an error if there is no consignment entry" in withContainers {
    container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    stubExternalServices(mappedPort)
    val unknownConsignmentId = UUID.randomUUID()
    val result = Main.run(List("export", "--consignmentId", unknownConsignmentId.toString, "--taskToken", "taskToken")).attempt.unsafeRunSync()

    result.isLeft should be(true)
    result.left.value.getMessage should equal(s"Cannot find a consignment for id $unknownConsignmentId")
  }

  "run" should "return an error if there is no metadata returned" in withContainers {
    container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, _, _)= stubExternalServices(mappedPort, shouldAddMetadata = false)

    val result = Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).attempt.unsafeRunSync()

    result.isLeft should be(true)
    result.left.value.getMessage should equal(s"Metadata for consignment $consignmentId is missing")
  }
}

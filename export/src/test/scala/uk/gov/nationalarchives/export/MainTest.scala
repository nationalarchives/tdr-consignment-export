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

import java.util.UUID
import scala.jdk.CollectionConverters.ListHasAsScala

class MainTest extends TestUtils {

  "run" should "copy the files to the output bucket" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileIds, _) = stubExternalServices(mappedPort)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val serveEvents = s3Server.getAllServeEvents.asScala
    val copyCount = serveEvents
      .count(req => req.getRequest.getMethod == RequestMethod.PUT && req.getRequest.getHeader("x-amz-copy-source") == s"clean/$consignmentId/${fileIds.head}")
    copyCount should equal(1)
  }

  "run" should "send a message to the SNS topic with the id of the file and the bucket name" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileIds, _) = stubExternalServices(mappedPort)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val snsMessage = snsServer.getAllServeEvents.asScala.head.getRequest.getFormParameters.get("Message").values().get(0)
    val fileOutput = decode[FileOutput](snsMessage).value
    fileOutput.bucket should equal("output")
    fileOutput.fileId should equal(fileIds.head)
  }

  "run" should "only write the body name, series name and consignment reference if there is no file or consignment metadata" in withContainers {
    container: PostgreSQLContainer =>
      val mappedPort = container.mappedPort(5432)
      val (consignmentId, fileIds, consignmentReference) = stubExternalServices(mappedPort)
      Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

      val serveEvents = s3Server.getAllServeEvents.asScala
      val metadataFileWriteBody = serveEvents
        .find(req => req.getRequest.getUrl == s"/${fileIds.head}.metadata" && req.getRequest.getMethod == RequestMethod.PUT)
        .map(_.getRequest.getBodyAsString)
        .getOrElse("")
      val jsonReturned = metadataFileWriteBody.split("\n").tail.head.trim

      JsonPath.read[String](jsonReturned, "$.PropertyName") shouldEqual "Value"
      JsonPath.read[String](jsonReturned, "$.Series") shouldEqual "Test"
      JsonPath.read[String](jsonReturned, "$.TransferInitiatedDatetime") shouldEqual "2024-08-29 00:00:00"
      JsonPath.read[String](jsonReturned, "$.ConsignmentReference") shouldEqual consignmentReference
      JsonPath.read[String](jsonReturned, "$.TransferringBody") shouldEqual "Test"
      JsonPath.read[String](jsonReturned, "$.MetadataSchemaLibraryVersion") shouldEqual "Schema-Library-Version-v0.1"

  }

  "run" should "write the file metadata where it exists" in withContainers {
    container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileIds, consignmentReference) = stubExternalServices(mappedPort)
    fileIds.foreach(fileId => addFFIDMetadata(fileId, mappedPort))
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val metadataFileWriteBody = getRequestBody(req => req.getRequest.getUrl == s"/${fileIds.head}.metadata" && req.getRequest.getMethod == RequestMethod.PUT)

    JsonPath.read[String](metadataFileWriteBody, "$.FFID[0].extension") shouldEqual "Extension"
    JsonPath.read[String](metadataFileWriteBody, "$.FFID[0].identificationBasis") shouldEqual "IdentificationBasis"
    JsonPath.read[String](metadataFileWriteBody, "$.FFID[0].puid") shouldEqual "PUID"
    JsonPath.read[Boolean](metadataFileWriteBody, "$.FFID[0].extensionMismatch") shouldEqual true
    JsonPath.read[String](metadataFileWriteBody, "$.PropertyName") shouldEqual "Value"
    JsonPath.read[String](metadataFileWriteBody, "$.Series") shouldEqual "Test"
    JsonPath.read[String](metadataFileWriteBody, "$.TransferInitiatedDatetime") shouldEqual "2024-08-29 00:00:00"
    JsonPath.read[String](metadataFileWriteBody, "$.ConsignmentReference") shouldEqual consignmentReference
    JsonPath.read[String](metadataFileWriteBody, "$.TransferringBody") shouldEqual "Test"
    JsonPath.read[String](metadataFileWriteBody, "$.MetadataSchemaLibraryVersion") shouldEqual "Schema-Library-Version-v0.1"
  }

  "run" should "return empty values if there are no ffid matches" in withContainers {
    container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileIds, consignmentReference) = stubExternalServices(mappedPort)
    fileIds.foreach(fileId => addFFIDMetadata(fileId, mappedPort, hasNullMatchValues = true))
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

   val metadataFileWriteBody = getRequestBody(req => req.getRequest.getUrl == s"/${fileIds.head}.metadata" && req.getRequest.getMethod == RequestMethod.PUT)

    JsonPath.read[Option[String]](metadataFileWriteBody,   "$.FFID[0].extension") shouldEqual null
    JsonPath.read[String](metadataFileWriteBody, "$.FFID[0].identificationBasis") shouldEqual "IdentificationBasis"
    JsonPath.read[Option[String]](metadataFileWriteBody,   "$.FFID[0].puid") shouldEqual null
    JsonPath.read[Boolean](metadataFileWriteBody,"$.FFID[0].extensionMismatch") shouldEqual true
    JsonPath.read[Option[String]](metadataFileWriteBody,   "$.FFID[0].formatName") shouldEqual null
    JsonPath.read[String](metadataFileWriteBody, "$.PropertyName") shouldEqual "Value"
    JsonPath.read[String](metadataFileWriteBody, "$.Series") shouldEqual "Test"
    JsonPath.read[String](metadataFileWriteBody, "$.TransferInitiatedDatetime") shouldEqual "2024-08-29 00:00:00"
    JsonPath.read[String](metadataFileWriteBody, "$.ConsignmentReference") shouldEqual consignmentReference
    JsonPath.read[String](metadataFileWriteBody, "$.TransferringBody") shouldEqual "Test"
    JsonPath.read[String](metadataFileWriteBody, "$.MetadataSchemaLibraryVersion") shouldEqual "Schema-Library-Version-v0.1"
  }

  "run" should "write the consignment metadata where it exists" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileIds, consignmentReference) = stubExternalServices(mappedPort)
    addConsignmentMetadata(consignmentId, mappedPort)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val metadataFileWriteBody = getRequestBody(req => req.getRequest.getUrl == s"/${fileIds.head}.metadata" && req.getRequest.getMethod == RequestMethod.PUT)
    JsonPath.read[java.util.List[Any]](metadataFileWriteBody, "$.FFID").asScala.toArray shouldEqual Array.empty[Any]
    JsonPath.read[String](metadataFileWriteBody, "$.PropertyName") shouldEqual "Value"
    JsonPath.read[String](metadataFileWriteBody, "$.Series") shouldEqual "Test"
    JsonPath.read[String](metadataFileWriteBody, "$.TransferInitiatedDatetime") shouldEqual "2024-08-29 00:00:00"
    JsonPath.read[String](metadataFileWriteBody, "$.ConsignmentReference") shouldEqual consignmentReference
    JsonPath.read[String](metadataFileWriteBody, "$.TransferringBody") shouldEqual "Test"
    JsonPath.read[String](metadataFileWriteBody, "$.MetadataSchemaLibraryVersion") shouldEqual "Schema-Library-Version-v0.1"

  }

  "run" should "add an OriginalFileReference field if this is a redacted record" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileIds, consignmentReference) = stubExternalServices(mappedPort, 2)
    val redactedMetadata = List(
      Metadata(fileIds.head, "OriginalFilepath", "/a/test/path"),
      Metadata(fileIds.last, "ClientSideOriginalFilepath", "/a/test/path"),
      Metadata(fileIds.last, "FileReference", "Z12345")
    )
    addFileMetadata(redactedMetadata, mappedPort, false)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val metadataFileWriteBody = getRequestBody(req => req.getRequest.getUrl == s"/${fileIds.head}.metadata" && req.getRequest.getMethod == RequestMethod.PUT)

    JsonPath.read[java.util.List[Any]](metadataFileWriteBody, "$.FFID").asScala.toArray shouldEqual Array.empty[Any]
    JsonPath.read[String](metadataFileWriteBody, "$.PropertyName") shouldEqual "Value"
    JsonPath.read[String](metadataFileWriteBody, "$.Series") shouldEqual "Test"
    JsonPath.read[String](metadataFileWriteBody, "$.OriginalFilepath") shouldEqual "/a/test/path"
    JsonPath.read[String](metadataFileWriteBody, "$.TransferInitiatedDatetime") shouldEqual "2024-08-29 00:00:00"
    JsonPath.read[String](metadataFileWriteBody, "$.ConsignmentReference") shouldEqual consignmentReference
    JsonPath.read[String](metadataFileWriteBody, "$.TransferringBody") shouldEqual "Test"
    JsonPath.read[String](metadataFileWriteBody, "$.MetadataSchemaLibraryVersion") shouldEqual "Schema-Library-Version-v0.1"
 }

  "run" should "return an error if there is no consignment entry" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    stubExternalServices(mappedPort)
    val unknownConsignmentId = UUID.randomUUID()
    val result = Main.run(List("export", "--consignmentId", unknownConsignmentId.toString, "--taskToken", "taskToken")).attempt.unsafeRunSync()

    result.isLeft should be(true)
    result.left.value.getMessage should equal(s"Cannot find a consignment for id $unknownConsignmentId")
  }

  "run" should "return an error if there is no metadata returned" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, _, _)= stubExternalServices(mappedPort, shouldAddMetadata = false)

    val result = Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).attempt.unsafeRunSync()

    result.isLeft should be(true)
    result.left.value.getMessage should equal(s"Metadata for consignment $consignmentId is missing")
  }
}

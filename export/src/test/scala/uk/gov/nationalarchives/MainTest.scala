package uk.gov.nationalarchives

import cats.effect.unsafe.implicits.global
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.github.tomakehurst.wiremock.http.RequestMethod
import org.scalatest.matchers.should.Matchers._

import java.util.UUID
import scala.jdk.CollectionConverters.ListHasAsScala

class MainTest extends TestUtils {

  "run" should "copy the files to the output bucket" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileId, _) = stubExternalServices(mappedPort)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()
    val serveEvents = s3Server.getAllServeEvents.asScala
    val copyCount = serveEvents
      .count(req => req.getRequest.getMethod == RequestMethod.PUT && req.getRequest.getHeader("x-amz-copy-source") == s"clean/$consignmentId/$fileId")
    copyCount should equal(1)
  }

  "run" should "only write the body name and consignment reference if there is no file or consignment metadata" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileId, consignmentReference) = stubExternalServices(mappedPort)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val expectedJson = s"""{"TransferringBody":"Test","ConsignmentReference":"$consignmentReference"}"""
    val serveEvents = s3Server.getAllServeEvents.asScala
    val metadataFileWriteBody = serveEvents
      .find(req => req.getRequest.getUrl == s"/$fileId.metadata" && req.getRequest.getMethod == RequestMethod.PUT)
      .map(_.getRequest.getBodyAsString)
      .getOrElse("")
    metadataFileWriteBody.split("\n").tail.head.trim should equal(expectedJson)
  }

  "run" should "write the file metadata where it exists" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileId, consignmentReference) = stubExternalServices(mappedPort)
    addFileMetadata(consignmentId, fileId, mappedPort)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val expectedJson = s"""{"FileMetadataTest":"TestValue","TransferringBody":"Test","ConsignmentReference":"$consignmentReference"}"""
    val metadataFileWriteBody = getRequestBody(req => req.getRequest.getUrl == s"/$fileId.metadata" && req.getRequest.getMethod == RequestMethod.PUT)
    metadataFileWriteBody should equal(expectedJson)
  }

  "run" should "write the consignment metadata where it exists" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    val (consignmentId, fileId, consignmentReference) = stubExternalServices(mappedPort)
    addConsignmentMetadata(consignmentId, mappedPort)
    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    val expectedJson = s"""{"ConsignmentMetadataTest":"TestValue","TransferringBody":"Test","ConsignmentReference":"$consignmentReference"}"""
    val metadataFileWriteBody = getRequestBody(req => req.getRequest.getUrl == s"/$fileId.metadata" && req.getRequest.getMethod == RequestMethod.PUT)
    metadataFileWriteBody should equal(expectedJson)
  }

  "run" should "do nothing if there are no files in the clean bucket" in withContainers { case container: PostgreSQLContainer =>
    val consignmentId = UUID.randomUUID()
    val mappedPort = container.mappedPort(5432)
    System.setProperty("db.port", mappedPort.toString)
    s3Server.resetAll()
    seedDatabase(mappedPort, consignmentId.toString)
    stubEmptyS3Get(consignmentId)

    Main.run(List("export", "--consignmentId", consignmentId.toString, "--taskToken", "taskToken")).unsafeRunSync()

    s3Server.getAllServeEvents.asScala.size should equal(1)
  }

  "run" should "return an error if there is no consignment entry" in withContainers { case container: PostgreSQLContainer =>
    val mappedPort = container.mappedPort(5432)
    stubExternalServices(mappedPort)
    val unknownConsignmentId = UUID.randomUUID()
    val result = Main.run(List("export", "--consignmentId", unknownConsignmentId.toString, "--taskToken", "taskToken")).attempt.unsafeRunSync()

    result.isLeft should be(true)
    result.left.value.getMessage should equal(s"Cannot find a consignment for id $unknownConsignmentId")
  }
}

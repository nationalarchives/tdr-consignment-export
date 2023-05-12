package uk.gov.nationalarchives.consignmentexport

import java.nio.file.{Files, Path, Paths}
import java.time.LocalDateTime
import java.util.UUID
import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import org.mockito.ArgumentCaptor
import software.amazon.awssdk.services.s3.model.{GetObjectResponse, PutObjectResponse}
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import cats.effect.unsafe.implicits.global
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.{Files => File}
import uk.gov.nationalarchives.consignmentexport.Config._

class S3FilesSpec extends ExportSpec {
  private val config = Configuration(
    S3("", "", "", "", 1, 0),
    Api(""),
    Auth("http://localhost:9002/auth", "tdr-backend-checks", "client-secret", "tdr"),
    EFS(""),
    StepFunction("")
  )

  "the downloadFiles method" should "call the library method with the correct arguments" in {
    val s3Utils = mock[S3Utils]
    val bucketCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val keyCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val pathCaptor: ArgumentCaptor[Option[Path]] = ArgumentCaptor.forClass(classOf[Option[Path]])
    val mockResponse = IO.pure(GetObjectResponse.builder.build())
    doAnswer(() => mockResponse).when(s3Utils).downloadFiles(bucketCaptor.capture(), keyCaptor.capture(), pathCaptor.capture())

    val consignmentId = UUID.randomUUID()
    val consignmentReference = "Consignment-Reference"
    val fileId = UUID.randomUUID()
    val fileMetadata = createMetadata(LocalDateTime.now())
    val metadata = File(
      fileId,
      "File".some,
      "name1".some,
      None,
      fileMetadata,
      None, None
    )

    val validatedMetadata = List(metadata)

    S3Files(s3Utils, config).downloadFiles(validatedMetadata, "testbucket", consignmentId, consignmentReference, "root").unsafeRunSync()

    bucketCaptor.getValue should equal("testbucket")
    keyCaptor.getValue should equal(s"$consignmentId/$fileId")
    pathCaptor.getValue.isDefined should equal(true)
    pathCaptor.getValue.get.toString should equal(s"root/$consignmentReference/originalPath")
  }

  "the downloadFiles method" should "call the library method with the correct arguments if there are quotes in the path" in {
    val s3Utils = mock[S3Utils]
    val pathCaptor: ArgumentCaptor[Option[Path]] = ArgumentCaptor.forClass(classOf[Option[Path]])
    val mockResponse = IO.pure(GetObjectResponse.builder.build())
    doAnswer(() => mockResponse).when(s3Utils).downloadFiles(any[String], any[String], pathCaptor.capture())

    val consignmentId = UUID.randomUUID()
    val consignmentReference = "Consignment-Reference"
    val fileId = UUID.randomUUID()
    val fileMetadata = createMetadata(LocalDateTime.now(), """a/path'with/quotes"""")
    val metadata = File(
      fileId,
      "File".some,
      "name".some,
      None,
      fileMetadata,
      None, None
    )
    val validatedMetadata = List(metadata)

    S3Files(s3Utils, config).downloadFiles(validatedMetadata, "testbucket", consignmentId, consignmentReference, "root").unsafeRunSync()

    pathCaptor.getValue.isDefined should equal(true)
    pathCaptor.getValue.get.toString should equal(s"""root/$consignmentReference/a/path'with/quotes"""")
  }

  "the downloadFiles method" should "call the library method for each file" in {
    val s3Utils = mock[S3Utils]
    val bucketCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val keyCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val pathCaptor: ArgumentCaptor[Option[Path]] = ArgumentCaptor.forClass(classOf[Option[Path]])
    val mockResponse = IO.pure(GetObjectResponse.builder.build())
    doAnswer(() => mockResponse).when(s3Utils).downloadFiles(bucketCaptor.capture(), keyCaptor.capture(), pathCaptor.capture())

    val consignmentId = UUID.randomUUID()
    val consignmentReference = "Consignment-Reference"
    val fileId1 = UUID.randomUUID()
    val fileId2 = UUID.randomUUID()
    val fileMetadata = createMetadata(LocalDateTime.now())
    val metadata1 = File(fileId1, "File".some, "name1".some, None, fileMetadata, None, None)
    val metadata2 = File(fileId2, "File".some, "name2".some, None, fileMetadata, None, None)

    val validatedMetadata = List(metadata1, metadata2)

    S3Files(s3Utils, config).downloadFiles(validatedMetadata, "testbucket", consignmentId, consignmentReference, "root").unsafeRunSync()

    verify(s3Utils, times(2)).downloadFiles(any[String], any[String], any[Option[Path]])
  }

  "the uploadFiles method" should "call the library method with the correct arguments" in {
    val s3Utils = mock[S3Utils]
    val bucketCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val keyCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val pathCaptor: ArgumentCaptor[Path] = ArgumentCaptor.forClass(classOf[Path])
    val mockResponse = IO.pure(PutObjectResponse.builder.build())
    doAnswer(() => mockResponse).when(s3Utils).upload(bucketCaptor.capture(), keyCaptor.capture(), pathCaptor.capture())

    val consignmentId = UUID.randomUUID()
    val consignmentRef = "TDR-2020-C57B"

    S3Files(s3Utils, config).uploadFiles("testbucket", consignmentId, consignmentRef, "fakepath").unsafeRunSync()

    bucketCaptor.getAllValues.forEach(b => b should equal("testbucket"))
    val keyValues = keyCaptor.getAllValues
    keyValues.get(0)  should equal(s"$consignmentRef.tar.gz")
    keyValues.get(1)  should equal(s"$consignmentRef.tar.gz.sha256")
    val pathValues = pathCaptor.getAllValues
    pathValues.get(0).toString  should equal("fakepath")
    pathValues.get(1).toString  should equal("fakepath.sha256")
  }

  "deleteDownloadDirectories" should "delete the efs directory" in {
    val s3Utils = mock[S3Utils]
    val testDir: Path = Paths.get("test_dir")
    val testFile: Path = Paths.get("test_dir", "test.txt")
    Files.createDirectories(testDir)
    Files.createFile(testFile)

    S3Files(s3Utils, config).deleteDownloadDirectories(testDir.toString).unsafeRunSync()

    Files.exists(testDir) shouldBe false
    Files.exists(testFile) shouldBe false
  }
}

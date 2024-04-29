package uk.gov.nationalarchives

import cats.effect.unsafe.implicits.global
import org.mockito.ArgumentCaptor
import org.mockito.scalatest.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model._
import uk.gov.nationalarchives.Main._
import uk.gov.nationalarchives.MetadataUtils._

import java.util.UUID
import scala.jdk.CollectionConverters.ListHasAsScala

class S3UtilsTest extends AnyFlatSpec with MockitoSugar with EitherValues with TableDrivenPropertyChecks {

  val config: Config = Config(Db(false, "", "", "", 5432), SFN(""), S3("", "testCleanBucket", "outputBucket", "outputBucketJudgment"), SNS("", "testTopic"))

  "copyFiles" should "copy the files returned by list objects" in {
    val client = mock[S3Client]
    val utils = new S3Utils(config, client)
    val consignmentId = UUID.randomUUID()
    val fileIdOne = UUID.randomUUID()
    val fileIdTwo = UUID.randomUUID()
    val listObjectsCaptor: ArgumentCaptor[ListObjectsV2Request] = ArgumentCaptor.forClass(classOf[ListObjectsV2Request])
    val copyObjectCaptor: ArgumentCaptor[CopyObjectRequest] = ArgumentCaptor.forClass(classOf[CopyObjectRequest])

    val s3ObjectOne = S3Object.builder.key(s"$consignmentId/$fileIdOne").build
    val s3ObjectTwo = S3Object.builder.key(s"$consignmentId/$fileIdTwo").build
    val response = ListObjectsV2Response.builder.contents(s3ObjectOne, s3ObjectTwo).build()

    when(client.listObjectsV2(listObjectsCaptor.capture())).thenReturn(response)
    when(client.copyObject(copyObjectCaptor.capture)).thenReturn(CopyObjectResponse.builder.build)

    utils.copyFiles(consignmentId, Standard).unsafeRunSync()

    val listObjectsResponse = listObjectsCaptor.getValue
    listObjectsResponse.bucket() should equal("testCleanBucket")
    listObjectsResponse.prefix() should equal(s"$consignmentId/")

    val copyObjectRequests = copyObjectCaptor.getAllValues.asScala
    copyObjectRequests.size should equal(2)
    copyObjectRequests.count(_.sourceKey() == s"$consignmentId/$fileIdOne") should equal(1)
    copyObjectRequests.count(_.sourceKey() == s"$consignmentId/$fileIdTwo") should equal(1)

    copyObjectRequests.count(_.destinationKey() == fileIdOne.toString) should equal(1)
    copyObjectRequests.count(_.destinationKey() == fileIdTwo.toString) should equal(1)
  }

  "copyFiles" should "not copy any files if there are none in the clean bucket" in {
    val client = mock[S3Client]
    val utils = new S3Utils(config, client)
    val consignmentId = UUID.randomUUID()

    val response = ListObjectsV2Response.builder.build()

    when(client.listObjectsV2(any[ListObjectsV2Request])).thenReturn(response)

    utils.copyFiles(consignmentId, Standard).unsafeRunSync()

    verify(client, times(0)).copyObject(any[CopyObjectRequest])
  }

  "copyFiles" should "return an error if list objects fails" in {
    val client = mock[S3Client]
    val utils = new S3Utils(config, client)
    val consignmentId = UUID.randomUUID()

    val s3Object = S3Object.builder.key(s"$consignmentId/${UUID.randomUUID()}").build
    val listObjectsV2Response = ListObjectsV2Response.builder.contents(s3Object).build
    when(client.listObjectsV2(any[ListObjectsV2Request])).thenReturn(listObjectsV2Response)

    when(client.copyObject(any[CopyObjectRequest])).thenThrow(new Exception("Error copying object"))

    val response = utils.copyFiles(consignmentId, Standard).attempt.unsafeRunSync()

    response.isLeft should equal(true)
    response.left.value.getMessage should equal("Error copying object")
  }

  "copyFiles" should "return an error if the copy operation fails" in {
    val client = mock[S3Client]
    val utils = new S3Utils(config, client)
    val consignmentId = UUID.randomUUID()

    when(client.listObjectsV2(any[ListObjectsV2Request])).thenThrow(new Exception("List objects has failed"))

    val response = utils.copyFiles(consignmentId, Standard).attempt.unsafeRunSync()

    response.isLeft should equal(true)
    response.left.value.getMessage should equal("List objects has failed")
  }

  val consignmentTypes: TableFor2[ConsignmentType, String] = Table(
    ("consignmentType", "outputBucket"),
    (Judgment, "outputBucketJudgment"),
    (Standard, "outputBucket")
  )

  forAll(consignmentTypes) { (consignmentType, bucket) =>
    "copyFiles" should s"write to the correct bucket for a $consignmentType export" in {
      val client = mock[S3Client]

      val utils = new S3Utils(config, client)
      val consignmentId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val copyObjectCaptor: ArgumentCaptor[CopyObjectRequest] = ArgumentCaptor.forClass(classOf[CopyObjectRequest])

      val s3Object = S3Object.builder.key(s"$consignmentId/$fileId").build
      val response = ListObjectsV2Response.builder.contents(s3Object).build()

      when(client.listObjectsV2(any[ListObjectsV2Request])).thenReturn(response)
      when(client.copyObject(copyObjectCaptor.capture)).thenReturn(CopyObjectResponse.builder.build)

      utils.copyFiles(consignmentId, consignmentType).unsafeRunSync()

      copyObjectCaptor.getValue.destinationBucket() should equal(bucket)
    }

    "createMetadata" should s"write to the correct bucket for a $consignmentType export" in {
      val client = mock[S3Client]

      val utils = S3Utils(config, client)
      val fileId = UUID.randomUUID()
      val putObjectRequestCaptor: ArgumentCaptor[PutObjectRequest] = ArgumentCaptor.forClass(classOf[PutObjectRequest])

      when(client.putObject(putObjectRequestCaptor.capture(), any[RequestBody])).thenReturn(PutObjectResponse.builder.build)

      utils.createMetadata(consignmentType, List(fileId), Nil, List(Metadata(UUID.randomUUID(), "Test", "TestValue"))).unsafeRunSync()

      putObjectRequestCaptor.getValue.bucket() should equal(bucket)
    }
  }

  "createMetadata" should s"write the correct data to the metadata file" in {
    val client = mock[S3Client]

    val utils = S3Utils(config, client)
    val fileId = UUID.randomUUID()
    val bodyCaptor: ArgumentCaptor[RequestBody] = ArgumentCaptor.forClass(classOf[RequestBody])

    when(client.putObject(any[PutObjectRequest], bodyCaptor.capture())).thenReturn(PutObjectResponse.builder.build)

    utils
      .createMetadata(
        Standard,
        List(fileId),
        List(Metadata(fileId, "TestFile", "TestFileValue")),
        List(Metadata(UUID.randomUUID(), "TestConsignment", "TestConsignmentValue"))
      )
      .unsafeRunSync()

    val body = bodyCaptor.getValue.contentStreamProvider().newStream().readAllBytes().map(_.toChar).mkString
    body should equal("""{"TestFile":"TestFileValue","TestConsignment":"TestConsignmentValue"}""")
  }

  "createMetadata" should s"not write metadata if the file is not in the list of file ids" in {
    val client = mock[S3Client]

    val utils = S3Utils(config, client)
    val fileId = UUID.randomUUID()
    val bodyCaptor: ArgumentCaptor[RequestBody] = ArgumentCaptor.forClass(classOf[RequestBody])

    when(client.putObject(any[PutObjectRequest], bodyCaptor.capture())).thenReturn(PutObjectResponse.builder.build)

    utils
      .createMetadata(
        Standard,
        List(fileId),
        List(Metadata(fileId, "TestFile1", "TestFileValue1"), Metadata(UUID.randomUUID(), "TestFile2", "TestFileValue2")),
        Nil
      )
      .unsafeRunSync()

    val body = bodyCaptor.getValue.contentStreamProvider().newStream().readAllBytes().map(_.toChar).mkString
    body should equal("""{"TestFile1":"TestFileValue1"}""")
  }

  "createMetadata" should s"return an error if there is an error writing to s3" in {
    val client = mock[S3Client]
    val fileId = UUID.randomUUID()
    val utils = S3Utils(config, client)

    when(client.putObject(any[PutObjectRequest], any[RequestBody])).thenThrow(new Exception("Error writing to S3"))

    val response = utils
      .createMetadata(
        Standard,
        List(fileId),
        List(Metadata(fileId, "TestFile1", "TestFileValue1")),
        Nil
      )
      .attempt
      .unsafeRunSync()

    response.isLeft should equal(true)
    response.left.value.getMessage should equal("Error writing to S3")
  }
}

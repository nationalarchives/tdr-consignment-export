package uk.gov.nationalarchives.`export`

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
import Main._
import MetadataUtils._
import uk.gov.nationalarchives.`export`.S3Utils.FileOutput

import java.util.UUID
import scala.jdk.CollectionConverters.ListHasAsScala

class S3UtilsTest extends AnyFlatSpec with MockitoSugar with EitherValues with TableDrivenPropertyChecks {

  val config: Config = Config(Db(useIamAuth = false, "", "", "", 5432), SFN(""), S3("", "testCleanBucket", "outputBucket", "outputBucketJudgment"), SNS("", "testTopic", 500))

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

    val consignmentMetadata = List(Metadata(UUID.randomUUID, "Series", "series"), Metadata(UUID.randomUUID, "TransferringBody", "body"))

    utils.copyFiles(consignmentId, Standard, consignmentMetadata).unsafeRunSync()

    val listObjectsResponse = listObjectsCaptor.getValue
    listObjectsResponse.bucket() should equal("testCleanBucket")
    listObjectsResponse.prefix() should equal(s"$consignmentId/")

    val copyObjectRequests = copyObjectCaptor.getAllValues.asScala
    copyObjectRequests.size should equal(2)
    copyObjectRequests.count(_.sourceKey() == s"$consignmentId/$fileIdOne") should equal(1)
    copyObjectRequests.count(_.sourceKey() == s"$consignmentId/$fileIdTwo") should equal(1)

    copyObjectRequests.count(_.destinationKey().startsWith(fileIdOne.toString)) should equal(1)
    copyObjectRequests.count(_.destinationKey().startsWith(fileIdTwo.toString)) should equal(1)
  }

  "copyFiles" should "return the correct number of files if the initial call to list objects is truncated" in {
    val client = mock[S3Client]
    val utils = new S3Utils(config, client)
    val consignmentId = UUID.randomUUID()
    val fileIdOne = UUID.randomUUID()
    val fileIdTwo = UUID.randomUUID()
    val listObjectsCaptor: ArgumentCaptor[ListObjectsV2Request] = ArgumentCaptor.forClass(classOf[ListObjectsV2Request])

    val s3ObjectOne = S3Object.builder.key(s"$consignmentId/$fileIdOne").build
    val s3ObjectTwo = S3Object.builder.key(s"$consignmentId/$fileIdTwo").build
    val responseOne = ListObjectsV2Response.builder.contents(s3ObjectOne).nextContinuationToken("continue").build()
    val responseTwo = ListObjectsV2Response.builder.contents(s3ObjectTwo).build()

    when(client.listObjectsV2(listObjectsCaptor.capture())).thenReturn(responseOne, responseTwo)
    when(client.copyObject(any[CopyObjectRequest])).thenReturn(CopyObjectResponse.builder.build)

    val consignmentMetadata = List(Metadata(UUID.randomUUID, "Series", "series"), Metadata(UUID.randomUUID, "TransferringBody", "body"))

    utils.copyFiles(consignmentId, Standard, consignmentMetadata).unsafeRunSync()

    val listObjectsResponses = listObjectsCaptor.getAllValues.asScala

    val firstCall = listObjectsResponses.last
    val lastCall = listObjectsResponses.head

    firstCall.bucket() should equal("testCleanBucket")
    firstCall.prefix() should equal(s"$consignmentId/")
    firstCall.continuationToken() should equal("continue")

    lastCall.bucket() should equal("testCleanBucket")
    lastCall.prefix() should equal(s"$consignmentId/")
    lastCall.continuationToken() should equal(null)
  }

  "copyFiles" should "not copy any files if there are none in the clean bucket" in {
    val client = mock[S3Client]
    val utils = new S3Utils(config, client)
    val consignmentId = UUID.randomUUID()

    val response = ListObjectsV2Response.builder.build()

    when(client.listObjectsV2(any[ListObjectsV2Request])).thenReturn(response)

    utils.copyFiles(consignmentId, Standard, Nil).unsafeRunSync()

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

    val response = utils.copyFiles(consignmentId, Standard, Nil).attempt.unsafeRunSync()

    response.isLeft should equal(true)
    response.left.value.getMessage should equal("Error copying object")
  }

  "copyFiles" should "return an error if the copy operation fails" in {
    val client = mock[S3Client]
    val utils = new S3Utils(config, client)
    val consignmentId = UUID.randomUUID()

    when(client.listObjectsV2(any[ListObjectsV2Request])).thenThrow(new Exception("List objects has failed"))

    val response = utils.copyFiles(consignmentId, Standard, Nil).attempt.unsafeRunSync()

    response.isLeft should equal(true)
    response.left.value.getMessage should equal("List objects has failed")
  }

  val consignmentTypes: TableFor2[ConsignmentType, String] = Table(
    ("consignmentType", "outputBucket"),
    (Judgment, "outputBucketJudgment"),
    (Standard, "outputBucket")
  )

  forAll(consignmentTypes) { (consignmentType, bucket) =>
    "copyFiles" should s"write the records to the correct bucket for a $consignmentType export" in {
      val client = mock[S3Client]

      val utils = new S3Utils(config, client)
      val consignmentId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val copyObjectCaptor: ArgumentCaptor[CopyObjectRequest] = ArgumentCaptor.forClass(classOf[CopyObjectRequest])

      val s3Object = S3Object.builder.key(s"$consignmentId/$fileId").build
      val response = ListObjectsV2Response.builder.contents(s3Object).build()
      val consignmentMetadata = List(Metadata(UUID.randomUUID, "Series", "series"), Metadata(UUID.randomUUID, "TransferringBody", "body"))

      when(client.listObjectsV2(any[ListObjectsV2Request])).thenReturn(response)
      when(client.copyObject(copyObjectCaptor.capture)).thenReturn(CopyObjectResponse.builder.build)

      utils.copyFiles(consignmentId, consignmentType, consignmentMetadata).unsafeRunSync()

      copyObjectCaptor.getValue.destinationBucket() should equal(bucket)
    }

    "createMetadata" should s"write the metadata to the correct bucket for a $consignmentType export" in {
      val client = mock[S3Client]

      val utils = S3Utils(config, client)
      val fileId = UUID.randomUUID()
      val putObjectRequestCaptor: ArgumentCaptor[PutObjectRequest] = ArgumentCaptor.forClass(classOf[PutObjectRequest])

      when(client.putObject(putObjectRequestCaptor.capture(), any[RequestBody])).thenReturn(PutObjectResponse.builder.build)

      utils.putMetadata(consignmentType, List(FileOutput("", fileId, UUID.randomUUID, None, None)), Nil, List(Metadata(UUID.randomUUID(), "Test", "TestValue")), Map.empty).unsafeRunSync()

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
      .putMetadata(
        Standard,
        List(FileOutput("", fileId, UUID.randomUUID, None, None)),
        List(Metadata(fileId, "TestFile", "TestFileValue")),
        List(Metadata(UUID.randomUUID(), "TestConsignment", "TestConsignmentValue")),
        Map.empty
      )
      .unsafeRunSync()

    val body = bodyCaptor.getValue.contentStreamProvider().newStream().readAllBytes().map(_.toChar).mkString
    body.startsWith("""[{"FFID":[],"TestFile":"TestFileValue","TestConsignment":"TestConsignmentValue","fileId":"""") should equal(true)
  }

  "createMetadata" should s"not write metadata if the file is not in the list of file ids" in {
    val client = mock[S3Client]

    val utils = S3Utils(config, client)
    val fileId = UUID.randomUUID()
    val bodyCaptor: ArgumentCaptor[RequestBody] = ArgumentCaptor.forClass(classOf[RequestBody])

    when(client.putObject(any[PutObjectRequest], bodyCaptor.capture())).thenReturn(PutObjectResponse.builder.build)

    utils
      .putMetadata(
        Standard,
        List(FileOutput("", fileId, UUID.randomUUID, None, None)),
        List(Metadata(fileId, "TestFile1", "TestFileValue1"), Metadata(UUID.randomUUID(), "TestFile2", "TestFileValue2")),
        Nil,
        Map.empty
      )
      .unsafeRunSync()

    val body = bodyCaptor.getValue.contentStreamProvider().newStream().readAllBytes().map(_.toChar).mkString
    body.startsWith("""[{"FFID":[],"TestFile1":"TestFileValue1","fileId":"""") should equal(true)
  }

  "createMetadata" should s"return an error if there is an error writing to s3" in {
    val client = mock[S3Client]
    val fileId = UUID.randomUUID()
    val utils = S3Utils(config, client)

    when(client.putObject(any[PutObjectRequest], any[RequestBody])).thenThrow(new Exception("Error writing to S3"))

    val response = utils
      .putMetadata(
        Standard,
        List(FileOutput("", fileId, UUID.randomUUID, None, None)),
        List(Metadata(fileId, "TestFile1", "TestFileValue1")),
        Nil,
        Map.empty
      )
      .attempt
      .unsafeRunSync()

    response.isLeft should equal(true)
    response.left.value.getMessage should equal("Error writing to S3")
  }
}

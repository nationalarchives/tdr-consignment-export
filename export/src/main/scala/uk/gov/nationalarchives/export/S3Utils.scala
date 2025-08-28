package uk.gov.nationalarchives.`export`

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, ListObjectsV2Request, PutObjectRequest, S3Object, Tagging, TaggingDirective}
import uk.gov.nationalarchives.`export`.Main.Config
import uk.gov.nationalarchives.`export`.MetadataUtils.{ConsignmentType, Judgment, Metadata, Standard}
import uk.gov.nationalarchives.`export`.S3Utils.FileOutput

import java.util.UUID
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

class S3Utils(config: Config, s3Client: S3Client) {

  @tailrec
  private def getAllObjects(prefix: String, currentObjects: List[S3Object] = Nil, continuationToken: Option[String] = None): List[S3Object] = {
    val listRequestBuilder = ListObjectsV2Request
      .builder()
      .bucket(config.s3.cleanBucket)
      .prefix(prefix)

    val listRequest = continuationToken.map(token => listRequestBuilder.continuationToken(token).build)
      .getOrElse(listRequestBuilder.build)

    val listObjectsResponse = s3Client.listObjectsV2(listRequest)
    val nextContinuationToken = Option(listObjectsResponse.nextContinuationToken())
    if(nextContinuationToken.isEmpty) {
      listObjectsResponse.contents().asScala.toList ++ currentObjects
    } else {
      getAllObjects(prefix, currentObjects ++ listObjectsResponse.contents().asScala.toList, nextContinuationToken)
    }
  }

  def copyFiles(consignmentId: UUID, consignmentType: ConsignmentType, consignmentMetadata: List[Metadata]): IO[List[FileOutput]] = IO.blocking {
    val destinationBucket = consignmentType match {
      case Judgment => config.s3.outputBucketJudgment
      case Standard => config.s3.outputBucket
    }

    getAllObjects(s"$consignmentId/")
      .map { s3Object =>
        val key = s3Object.key
        val fileId = UUID.randomUUID()
        val assetId = key.split("/").last
        val destinationKey = s"$assetId/$fileId"
        val copyRequest = CopyObjectRequest
          .builder()
          .sourceKey(key)
          .sourceBucket(config.s3.cleanBucket)
          .destinationKey(destinationKey)
          .destinationBucket(destinationBucket)
          .taggingDirective(TaggingDirective.REPLACE)
          .tagging(Tagging.builder().build())
          .build()
        s3Client.copyObject(copyRequest)
        val series = consignmentMetadata.find(_.propertyName == "Series").map(_.value)
        val body = consignmentMetadata.find(_.propertyName == "TransferringBody").map(_.value)
        FileOutput(destinationBucket, UUID.fromString(assetId), fileId, body, series)
      }
  }

  def putMetadata(
     consignmentType: ConsignmentType,
     fileOutputs: List[FileOutput],
     fileMetadata: List[Metadata],
     consignmentMetadata: List[Metadata],
     ffidMetadata: Map[UUID, List[MetadataUtils.FFID]]
  ): IO[Unit] = IO.blocking {
    val groupedMetadata = fileMetadata.groupBy(_.id)
    val outputBucket = consignmentType match {
      case Judgment => config.s3.outputBucketJudgment
      case Standard => config.s3.outputBucket
    }

    def jsonFromMetadata(metadata: List[Metadata]): Map[String, Json] =
      metadata.map(m => m.propertyName -> Json.fromString(m.value)).toMap

    fileOutputs.foreach { fileOutput =>
      val ffidArray = ffidMetadata.getOrElse(fileOutput.assetId, Nil)
      val metadataJson = groupedMetadata
        .get(fileOutput.assetId)
        .map(jsonFromMetadata)
        .getOrElse(Map.empty) ++ jsonFromMetadata(consignmentMetadata) ++ Map("fileId" -> Json.fromString(fileOutput.fileId.toString))
      val ffidObject = JsonObject.fromMap(Map("FFID" -> ffidArray.asJson))
      val allObjects = JsonObject
        .fromMap(metadataJson)
        .deepMerge(ffidObject)
        .toJson

      val body = RequestBody.fromString(Json.arr(allObjects).noSpaces)
      val request = PutObjectRequest.builder.bucket(outputBucket).key(s"${fileOutput.assetId}.metadata").build
      s3Client.putObject(request, body)
    }
  }
}

object S3Utils {
  def apply(config: Config, s3Client: S3Client) = new S3Utils(config, s3Client)

  case class FileOutput(bucket: String, assetId: UUID, fileId: UUID, transferringBody: Option[String], series: Option[String])
}

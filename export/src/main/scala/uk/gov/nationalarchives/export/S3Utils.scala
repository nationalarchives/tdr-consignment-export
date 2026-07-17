package uk.gov.nationalarchives.`export`

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Json, JsonObject}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, ListObjectsV2Request, PutObjectRequest, S3Object, Tag, Tagging, TaggingDirective}
import uk.gov.nationalarchives.`export`.Main.Config
import uk.gov.nationalarchives.`export`.MetadataUtils.{ConsignmentId, ConsignmentType, Judgment, Metadata, Series, Standard, TransferringBody, UserId}
import uk.gov.nationalarchives.`export`.ObjectKeyIdHandler.ObjectKeyIds
import uk.gov.nationalarchives.`export`.S3Utils.{FileDetails, FileOutput}

import java.net.URI
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

  private def contextTagging(userId: UUID, consignmentId: UUID) = {
    if (config.exportConfiguration.blockAddContextTagging) {
      Tagging.builder().build()
    } else {
      val consignmentIdTag = Tag.builder
        .key(ConsignmentId.id)
        .value(consignmentId.toString)
        .build
      val userIdTag = Tag.builder
        .key(UserId.id)
        .value(userId.toString)
        .build
      Tagging.builder()
        .tagSet(consignmentIdTag, userIdTag)
        .build()
    }
  }

  def copyFiles(userId: UUID, consignmentId: UUID, consignmentType: ConsignmentType, consignmentMetadata: List[Metadata],
                objectKeyIds: Map[UUID, ObjectKeyIds]): IO[List[FileDetails]] = IO.blocking {
    val destinationBucket = consignmentType match {
      case Judgment => config.s3.outputBucketJudgment
      case Standard => config.s3.outputBucket
    }

    getAllObjects(s"$consignmentId/")
      .map { s3Object =>
        val key = s3Object.key
        val tdrFileId = UUID.fromString(key.split("/").last)
        val ids = objectKeyIds(tdrFileId)
        val destinationKey = s"${ids.assetId}/${ids.digitalObjectKeyId}"
        val copyRequest = CopyObjectRequest
          .builder()
          .sourceKey(key)
          .sourceBucket(config.s3.cleanBucket)
          .destinationKey(destinationKey)
          .destinationBucket(destinationBucket)
          .taggingDirective(TaggingDirective.REPLACE)
          .tagging(contextTagging(userId, consignmentId))
          .build()
        s3Client.copyObject(copyRequest)
        val series = consignmentMetadata.find(_.propertyName == Series.id).map(_.value)
        val body = consignmentMetadata.find(_.propertyName == TransferringBody.id).map(_.value)
        val metadataLocation = URI.create(s"s3://$destinationBucket/${ids.assetId}.metadata")
        val output = FileOutput(destinationBucket, ids.assetId, ids.digitalObjectKeyId, metadataLocation, body, series)
        FileDetails(output, ids)
      }
  }

  def putMetadata(
                   userId: UUID,
                   consignmentId: UUID,
                   consignmentType: ConsignmentType,
                   fileDetails: List[FileDetails],
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

    fileDetails.foreach { fileDetails =>
      val ids = fileDetails.objectKeyIds
      val ffidArray = ffidMetadata.getOrElse(ids.tdrFileId, Nil)
      val metadataJson = groupedMetadata
        .get(ids.tdrFileId)
        .map(jsonFromMetadata)
        .getOrElse(Map.empty) ++ jsonFromMetadata(consignmentMetadata) ++ Map("fileId" -> Json.fromString(ids.digitalObjectKeyId.toString))
      val ffidObject = JsonObject.fromMap(Map("FFID" -> ffidArray.asJson))
      val allObjects = JsonObject
        .fromMap(metadataJson)
        .deepMerge(ffidObject)
        .toJson

      val body = RequestBody.fromString(Json.arr(allObjects).noSpaces)
      val request = PutObjectRequest.builder
        .bucket(outputBucket)
        .key(fileDetails.output.metadataLocation.getPath.drop(1))
        .tagging(contextTagging(userId, consignmentId))
        .build
      s3Client.putObject(request, body)
    }
  }
}

object S3Utils {
  def apply(config: Config, s3Client: S3Client) = new S3Utils(config, s3Client)
  case class FileDetails(output: FileOutput, objectKeyIds: ObjectKeyIds)
  case class FileOutput(bucket: String, assetId: UUID, fileId: UUID, metadataLocation: URI,
                        transferringBody: Option[String], series: Option[String])
}

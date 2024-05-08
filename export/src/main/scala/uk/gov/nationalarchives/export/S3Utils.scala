package uk.gov.nationalarchives.`export`

import cats.effect.IO
import io.circe.{Json, JsonObject, Printer}
import io.circe.generic.auto._
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, ListObjectsV2Request, PutObjectRequest}
import Main.Config
import MetadataUtils._
import S3Utils.FileOutput
import io.circe.syntax._
import Main.Config
import MetadataUtils.{ConsignmentType, Judgment, Metadata, Standard}

import scala.jdk.CollectionConverters._
import java.util.UUID

class S3Utils(config: Config, s3Client: S3Client) {

  def copyFiles(consignmentId: UUID, consignmentType: ConsignmentType): IO[List[FileOutput]] = IO.blocking {
    val listRequest = ListObjectsV2Request.builder().bucket(config.s3.cleanBucket).prefix(s"$consignmentId/").build
    val objects = s3Client.listObjectsV2(listRequest)
    val destinationBucket = consignmentType match {
      case Judgment => config.s3.outputBucketJudgment
      case Standard => config.s3.outputBucket
    }
    objects
      .contents()
      .asScala
      .map { s3Object =>
        val key = s3Object.key
        val destinationKey = key.split("/").last
        val copyRequest = CopyObjectRequest
          .builder()
          .sourceKey(key)
          .sourceBucket(config.s3.cleanBucket)
          .destinationKey(destinationKey)
          .destinationBucket(destinationBucket)
          .build()
        s3Client.copyObject(copyRequest)
        FileOutput(destinationBucket, UUID.fromString(destinationKey))
      }
      .toList
  }

  def putMetadata(
      consignmentType: ConsignmentType,
      fileIds: List[UUID],
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

    fileIds.foreach { fileId =>
      val ffidArray = ffidMetadata.getOrElse(fileId, Nil)
      val metadataJson = groupedMetadata
        .get(fileId)
        .map(jsonFromMetadata)
        .getOrElse(Map.empty) ++ jsonFromMetadata(consignmentMetadata)
      val ffidObject = JsonObject.fromMap(Map("FFID" -> ffidArray.asJson))
      val allObjects = JsonObject
        .fromMap(metadataJson)
        .deepMerge(ffidObject)
        .toJson
        .printWith(Printer.noSpaces)
      val body = RequestBody.fromString(allObjects)
      val request = PutObjectRequest.builder.bucket(outputBucket).key(s"$fileId.metadata").build
      s3Client.putObject(request, body)
    }
  }
}

object S3Utils {
  def apply(config: Config, s3Client: S3Client) = new S3Utils(config, s3Client)

  case class FileOutput(bucket: String, fileId: UUID)
}

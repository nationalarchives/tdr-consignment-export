package uk.gov.nationalarchives

import cats.effect.IO
import io.circe.{Json, JsonObject, Printer}
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, ListObjectsV2Request, PutObjectRequest}
import uk.gov.nationalarchives.Main.Config
import uk.gov.nationalarchives.MetadataUtils._

import scala.jdk.CollectionConverters._
import java.util.UUID

class S3Utils(config: Config, s3Client: S3Client) {

  def copyFiles(consignmentId: UUID, consignmentType: ConsignmentType): IO[List[UUID]] = IO.blocking {
    val listRequest = ListObjectsV2Request.builder().bucket(config.s3.cleanBucket).prefix(s"$consignmentId/").build
    val objects = s3Client.listObjectsV2(listRequest)
    val destinationBucket = consignmentType match {
      case Judgment => config.s3.outputBucketJudgment
      case Standard => config.s3.outputBucket
    }
    objects.contents().asScala.map { s3Object =>
      val key = s3Object.key
      val copyRequest = CopyObjectRequest.builder()
        .sourceKey(key)
        .sourceBucket(config.s3.cleanBucket)
        .destinationKey(key)
        .destinationBucket(destinationBucket)
        .build()
      s3Client.copyObject(copyRequest)
      UUID.fromString(key)
    }.toList
  }

  def createMetadata(fileIds: List[UUID], metadata: List[Metadata]): IO[Unit] = IO.blocking {
    val groupedMetadata = metadata.groupBy(_.fileId)
    fileIds.foreach { fileId =>
      val metadataJson = groupedMetadata.get(fileId).map { metadata =>
        metadata.map(m => (m.PropertyName -> Json.fromString(m.Value))).toMap
      }.getOrElse(Map.empty)

      val body = RequestBody.fromString(JsonObject.fromMap(metadataJson).toJson.printWith(Printer.noSpaces))
      val request = PutObjectRequest.builder.bucket(config.s3.outputBucket).key(fileId.toString).build
      s3Client.putObject(request, body)
    }
  }
}
object S3Utils {
  def apply(config: Config, s3Client: S3Client) = new S3Utils(config, s3Client)
}

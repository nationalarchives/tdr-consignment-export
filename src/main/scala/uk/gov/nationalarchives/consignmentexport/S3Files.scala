package uk.gov.nationalarchives.consignmentexport

import java.io.File
import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.typesafe.config.ConfigFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import uk.gov.nationalarchives.aws.utils.S3Utils
import uk.gov.nationalarchives.consignmentexport.Config.Configuration
import uk.gov.nationalarchives.consignmentexport.Main.directoryType
import uk.gov.nationalarchives.consignmentexport.Utils._
import uk.gov.nationalarchives.consignmentexport.Validator.ValidatedFileMetadata

import scala.concurrent.duration._
import scala.language.postfixOps

class S3Files(s3Utils: S3Utils, config: Configuration)(implicit val logger: SelfAwareStructuredLogger[IO]) {
  private val downloadBatchSize = config.s3.downloadFilesBatchSize
  private val downloadDelay = config.s3.downloadBatchDelayMs

  def createDownloadDirectories(files: List[ValidatedFileMetadata], consignmentReference: String, rootLocation: String): IO[List[Boolean]] = {
    IO {
      new File(s"$rootLocation/$consignmentReference").mkdirs()
      files.filter(_.fileType == "Folder").map(f => new File(s"$rootLocation/$consignmentReference/${f.clientSideOriginalFilePath}").mkdirs())
    }
  }

  def batchDownloadingFiles(batch: List[ValidatedFileMetadata], bucket: String, consignmentId: UUID, consignmentReference: String, rootLocation: String): IO[List[GetObjectResponse]] = {
    IO.sleep(downloadDelay milliseconds).flatMap(_ => {
      batch.map(file =>
        s3Utils.downloadFiles(
          bucket,
          s"$consignmentId/${file.fileId}",
          s"$rootLocation/$consignmentReference/${file.clientSideOriginalFilePath}".toPath.some
        )).sequence
    })
  }

  def downloadFiles(files: List[ValidatedFileMetadata], bucket: String, consignmentId: UUID, consignmentReference: String, rootLocation: String): IO[Unit] = {
    for {
      _ <- createDownloadDirectories(files, consignmentReference, rootLocation)
      _ <- files.filter(_.fileType != directoryType).grouped(downloadBatchSize).toSeq.map(batchDownloadingFiles(_, bucket, consignmentId, consignmentReference, rootLocation)).sequence
      _ <- logger.info(s"Files downloaded from S3 for consignment $consignmentId")
    } yield()
  }

  def uploadFiles(bucket: String, consignmentId: UUID, consignmentReference: String, tarPath: String): IO[Unit] = for {
    _ <- s3Utils.upload(bucket, s"$consignmentReference.tar.gz", tarPath.toPath)
    _ <- s3Utils.upload(bucket, s"$consignmentReference.tar.gz.sha256", s"$tarPath.sha256".toPath)
    _ <- logger.info(s"Files uploaded to S3 for consignment $consignmentId, consignment reference: $consignmentReference")
  } yield ()
}

object S3Files {
  def apply(s3Utils: S3Utils, config: Configuration)(implicit logger: SelfAwareStructuredLogger[IO]): S3Files = new S3Files(s3Utils, config)(logger)
}

package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import cats.implicits._
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import org.typelevel.log4cats.SelfAwareStructuredLogger
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.consignmentexport.Config.Configuration
import uk.gov.nationalarchives.consignmentexport.Utils._

import java.io.File
import java.util.UUID
import scala.concurrent.duration._
import scala.language.postfixOps

class S3Files(s3Utils: S3Utils, config: Configuration)(implicit val logger: SelfAwareStructuredLogger[IO]) {
  private val downloadBatchSize = config.s3.downloadFilesBatchSize

  private val downloadDelay = config.s3.downloadBatchDelayMs

  def createDownloadDirectories(files: List[Files], consignmentReference: String, rootLocation: String): IO[List[Boolean]] = {
    IO {
      new File(s"$rootLocation/$consignmentReference").mkdirs()
      files.filter(_.isFolder).map(f =>
        new File(s"$rootLocation/$consignmentReference/${f.getClientSideOriginalFilePath}").mkdirs()
      )
    }
  }

  def batchDownloadingFiles(batch: List[Files], bucket: String, consignmentId: UUID, consignmentReference: String, rootLocation: String): IO[List[GetObjectResponse]] = {
    IO.sleep(downloadDelay milliseconds).flatMap(_ => {
      batch.map(file =>
        s3Utils.downloadFiles(
          bucket,
          s"$consignmentId/${file.fileId}",
          s"$rootLocation/$consignmentReference/${file.getClientSideOriginalFilePath}".toPath.some
        )).sequence
    })
  }

  def downloadFiles(files: List[Files], bucket: String, consignmentId: UUID, consignmentReference: String, rootLocation: String): IO[Unit] = {
    for {
      _ <- createDownloadDirectories(files, consignmentReference, rootLocation)
      _ <- files.filter(!_.isFolder)
        .grouped(downloadBatchSize).toSeq.map(batchDownloadingFiles(_, bucket, consignmentId, consignmentReference, rootLocation)).sequence
      _ <- logger.info(s"Files downloaded from S3 for consignment $consignmentId")
    } yield ()
  }

  def uploadFiles(bucket: String, consignmentId: UUID, consignmentReference: String, tarPath: String): IO[Unit] = for {
    _ <- s3Utils.upload(bucket, s"$consignmentReference.tar.gz", tarPath.toPath)
    _ <- s3Utils.upload(bucket, s"$consignmentReference.tar.gz.sha256", s"$tarPath.sha256".toPath)
    _ <- logger.info(s"Files uploaded to S3 for consignment $consignmentId, consignment reference: $consignmentReference")
  } yield ()

  def deleteDownloadDirectories(rootLocation: String): IO[Unit] = {
    val file = new File(s"$rootLocation")

    def deleteRecursively(file: File): Unit = {
      if (file.isDirectory) {
        file.listFiles.foreach(deleteRecursively)
      }
      if (file.exists && !file.delete) {
        IO.raiseError(throw new Exception(s"Unable to delete ${file.getAbsolutePath}"))
      }
    }

    for {
      _ <- logger.info(s"Deleting directory $rootLocation from EFS")
      _ <- IO(deleteRecursively(file)).onError(e => logger.error(e.getMessage))
    } yield ()
  }
}

object S3Files {
  def apply(s3Utils: S3Utils, config: Configuration)(implicit logger: SelfAwareStructuredLogger[IO]): S3Files = new S3Files(s3Utils, config)(logger)
}

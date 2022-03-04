package uk.gov.nationalarchives.consignmentexport

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID

import cats.effect._
import cats.syntax.all._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import com.typesafe.config.ConfigFactory
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import uk.gov.nationalarchives.aws.utils.Clients.{s3Async, sfnAsyncClient}
import uk.gov.nationalarchives.aws.utils.{S3Utils, StepFunctionUtils}
import uk.gov.nationalarchives.consignmentexport.Arguments._
import uk.gov.nationalarchives.consignmentexport.BagMetadata.{InternalSenderIdentifierKey, SourceOrganisationKey}
import uk.gov.nationalarchives.consignmentexport.Config.config
import uk.gov.nationalarchives.consignmentexport.GraphQlApi.backend
import uk.gov.nationalarchives.consignmentexport.StepFunction.ExportOutput
import uk.gov.nationalarchives.tdr.keycloak.TdrKeycloakDeployment

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

object Main extends CommandIOApp("tdr-consignment-export", "Exports tdr files in bagit format", version = "0.0.1") {
  private val configuration = ConfigFactory.load()

  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment(configuration.getString("auth.url"), "tdr", 3600)
  private val stepFunctionPublishEndpoint = configuration.getString("stepFunction.endpoint")

  val directoryType = "Folder"

  override def main: Opts[IO[ExitCode]] =
     exportOps.map {
      case FileExport(consignmentId, taskToken) =>
        val exportFailedErrorMessage = s"Export for consignment $consignmentId failed"
        val stepFunction: StepFunction  = StepFunction(StepFunctionUtils(sfnAsyncClient(stepFunctionPublishEndpoint)))
        def runHeartbeat(): IO[Unit] = stepFunction.sendHeartbeat(taskToken) >> IO.sleep(30 seconds) >> runHeartbeat()
        val exitCode = for {
          heartbeat <- runHeartbeat().start
          config <- config()
          rootLocation = config.efs.rootLocation
          exportId = UUID.randomUUID
          basePath = s"$rootLocation/$exportId"
          bashCommands = BashCommands()
          graphQlApi = GraphQlApi(config.api.url)
          keycloakClient = KeycloakClient(config)
          s3Files = S3Files(S3Utils(s3Async))
          bagit = Bagit()
          validator = Validator(consignmentId)
          //Export datetime generated as value needed in bag metadata and DB table
          //Cannot use the value from DB table in bag metadata, as bag metadata created before bagging
          //and cannot update DB until bag creation successfully completed
          exportDatetime = ZonedDateTime.now(ZoneOffset.UTC)
          consignmentResult <- graphQlApi.getConsignmentMetadata(config, consignmentId)
          consignmentData <- IO.fromEither(validator.validateConsignmentResult(consignmentResult))
          _ <- IO.fromEither(validator.validateConsignmentHasFiles(consignmentData))
          bagMetadata <- BagMetadata(keycloakClient).generateMetadata(consignmentId, consignmentData, exportDatetime)
          validatedFileMetadata <- IO.fromEither(validator.extractFileMetadata(consignmentData.files))
          validatedFfidMetadata <- IO.fromEither(validator.extractFFIDMetadata(consignmentData.files))
          validatedAntivirusMetadata <- IO.fromEither(validator.extractAntivirusMetadata(consignmentData.files))
          _ <- s3Files.downloadFiles(validatedFileMetadata, config.s3.cleanBucket, consignmentId, consignmentData.consignmentReference, basePath)
          bag <- bagit.createBag(consignmentData.consignmentReference, basePath, bagMetadata)
          checkSumMismatches = ChecksumValidator().findChecksumMismatches(bag, validatedFileMetadata)
          _ = if(checkSumMismatches.nonEmpty) throw new RuntimeException(s"Checksum mismatch for file(s): ${checkSumMismatches.mkString("\n")}")
          bagAdditionalFiles = BagAdditionalFiles(bag.getRootDir)
          fileMetadataCsv <- bagAdditionalFiles.createFileMetadataCsv(validatedFileMetadata)
          ffidMetadataCsv <- bagAdditionalFiles.createFfidMetadataCsv(validatedFfidMetadata)
          antivirusCsv <- bagAdditionalFiles.createAntivirusMetadataCsv(validatedAntivirusMetadata)
          checksums <- ChecksumCalculator().calculateChecksums(fileMetadataCsv, ffidMetadataCsv, antivirusCsv)
          _ <- bagit.writeTagManifestRows(bag, checksums)
          // The owner and group in the below command have no effect on the file permissions. It just makes tar idempotent
          consignmentReference = consignmentData.consignmentReference
          tarPath = s"$basePath/$consignmentReference.tar.gz"
          _ <- bashCommands.runCommand(s"tar --sort=name --owner=root:0 --group=root:0 --mtime ${java.time.LocalDate.now.toString} -C $basePath -c ./${consignmentData.consignmentReference} | gzip -n > $tarPath")
          _ <- bashCommands.runCommand(s"sha256sum $tarPath > $tarPath.sha256")
          consignmentType = consignmentData.consignmentType.getOrElse("standard")
          s3Bucket = if(consignmentType.contains("judgment")) { config.s3.outputBucketJudgment } else { config.s3.outputBucket}
          _ <- s3Files.uploadFiles(s3Bucket, consignmentId, consignmentReference, tarPath)
          _ <- graphQlApi.updateExportLocation(config, consignmentId, s"s3://$s3Bucket/$consignmentReference.tar.gz", exportDatetime)
          _ <- stepFunction.publishSuccess(taskToken,
            ExportOutput(consignmentData.userid,
              bagMetadata.get(InternalSenderIdentifierKey).get(0),
              bagMetadata.get(SourceOrganisationKey).get(0),
              consignmentType,
              s3Bucket
            ))
          _ <- heartbeat.cancel
        } yield ExitCode.Success

        exitCode.handleErrorWith(e => {
          for {
            _ <- stepFunction.publishFailure(taskToken, s"$exportFailedErrorMessage: ${e.getMessage}")
            _ <- IO.raiseError(e)
          } yield ExitCode.Error
        })
    }
}

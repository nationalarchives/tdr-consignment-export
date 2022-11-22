package uk.gov.nationalarchives.consignmentexport

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.UUID
import cats.effect._
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import com.typesafe.config.ConfigFactory
import gov.loc.repository.bagit.domain.Metadata
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gce}
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import uk.gov.nationalarchives.aws.utils.s3.S3Clients.s3Async
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionClients.sfnAsyncClient
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionUtils
import uk.gov.nationalarchives.consignmentexport.Arguments._
import uk.gov.nationalarchives.consignmentexport.BagMetadata.{InternalSenderIdentifierKey, SourceOrganisationKey}
import uk.gov.nationalarchives.consignmentexport.Config.{Configuration, config}
import uk.gov.nationalarchives.consignmentexport.GraphQlApi.backend
import uk.gov.nationalarchives.consignmentexport.StepFunction.ExportOutput
import uk.gov.nationalarchives.tdr.keycloak.TdrKeycloakDeployment
import uk.gov.nationalarchives.consignmentexport.BuildInfo.version
import uk.gov.nationalarchives.consignmentexport.ConsignmentStatus.{StatusType, StatusValue}

import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

object Main extends CommandIOApp("tdr-consignment-export", "Exports tdr files in bagit format", version = version) {
  private val configuration = ConfigFactory.load()

  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment(configuration.getString("auth.url"), "tdr", 3600)
  private val stepFunctionPublishEndpoint = configuration.getString("stepFunction.endpoint")

  override def main: Opts[IO[ExitCode]] =
    exportOps.map {
      case FileExport(consignmentId, taskToken) =>
        val exportFailedErrorMessage = s"Export for consignment $consignmentId failed"
        val stepFunction: StepFunction = StepFunction(StepFunctionUtils(sfnAsyncClient(stepFunctionPublishEndpoint)))

        def runHeartbeat(): IO[Unit] = stepFunction.sendHeartbeat(taskToken) >> IO.sleep(30 seconds) >> runHeartbeat()

        val exitCode = for {
          heartbeat <- runHeartbeat().start
          config <- config()
          rootLocation = config.efs.rootLocation
          exportId = UUID.randomUUID
          basePath = s"$rootLocation/$exportId"
          bashCommands = BashCommands()
          graphQlApi = GraphQlApi(config, consignmentId)
          s3Files = S3Files(S3Utils(s3Async(config.s3.endpoint)), config)
          validator = Validator(consignmentId)
          //Export datetime generated as value needed in bag metadata and DB table
          //Cannot use the value from DB table in bag metadata, as bag metadata created before bagging
          //and cannot update DB until bag creation successfully completed
          exportDatetime = ZonedDateTime.now(ZoneOffset.UTC)
          consignmentResult <- graphQlApi.getConsignmentMetadata
          consignmentData <- IO.fromEither(validator.validateConsignmentResult(consignmentResult))
          customMetadata <- graphQlApi.getCustomMetadata
          _ <- IO.fromEither(validator.validateConsignmentHasFiles(consignmentData))
          _ <- s3Files.downloadFiles(consignmentData.files, config.s3.cleanBucket, consignmentId, consignmentData.consignmentReference, basePath)
          bagMetadata <- createBag(consignmentId, consignmentData, customMetadata, exportDatetime, config, basePath)

          // The owner and group in the below command have no effect on the file permissions. It just makes tar idempotent
          consignmentReference = consignmentData.consignmentReference
          tarPath = s"$basePath/$consignmentReference.tar.gz"
          _ <- bashCommands.runCommand(s"tar --sort=name --owner=root:0 --group=root:0 --mtime ${java.time.LocalDate.now.toString} -C $basePath -c ./${consignmentData.consignmentReference} | gzip -n > $tarPath")
          _ <- bashCommands.runCommand(s"sha256sum $tarPath > $tarPath.sha256")
          consignmentType = consignmentData.consignmentType.getOrElse("standard")
          s3Bucket = if (consignmentType.contains("judgment")) {
            config.s3.outputBucketJudgment
          } else {
            config.s3.outputBucket
          }
          _ <- s3Files.uploadFiles(s3Bucket, consignmentId, consignmentReference, tarPath)
          _ <- graphQlApi.updateExportData(s"s3://$s3Bucket/$consignmentReference.tar.gz", exportDatetime, version)
          _ <- stepFunction.publishSuccess(taskToken,
            ExportOutput(consignmentData.userid,
              bagMetadata.get(InternalSenderIdentifierKey).get(0),
              bagMetadata.get(SourceOrganisationKey).get(0),
              consignmentType,
              s3Bucket
            ))
          _ <- graphQlApi.updateConsignmentStatus(StatusType.export, StatusValue.completed)
          _ <- heartbeat.cancel
        } yield ExitCode.Success

        exitCode.handleErrorWith {e =>
          for {
            _ <- stepFunction.publishFailure(taskToken, s"$exportFailedErrorMessage: ${e.getMessage}")
            _ <- IO.raiseError(e)
          } yield ExitCode.Error
        }
    }


    def createBag(consignmentId: UUID, consignmentData: gce.GetConsignment, customMetadata: List[CustomMetadata], exportDatetime: ZonedDateTime, config: Configuration, basePath: String): IO[Metadata] = {
      val bagit = Bagit()
      val keycloakClient = KeycloakClient(config)
      val validator = Validator(consignmentId)
      for {
        bagMetadata <- BagMetadata(keycloakClient).generateMetadata(consignmentId, consignmentData, exportDatetime)
        validatedFfidMetadata <- IO.fromEither(validator.extractFFIDMetadata(consignmentData.files))
        validatedAntivirusMetadata <- IO.fromEither(validator.extractAntivirusMetadata(consignmentData.files))
        bag <- bagit.createBag(consignmentData.consignmentReference, basePath, bagMetadata)
        checkSumMismatches = ChecksumValidator().findChecksumMismatches(bag, consignmentData.files)
        _ = if (checkSumMismatches.nonEmpty) throw new RuntimeException(s"Checksum mismatch for file(s): ${checkSumMismatches.mkString("\n")}")
        bagAdditionalFiles = BagAdditionalFiles(bag.getRootDir)
        fileMetadataCsv <- bagAdditionalFiles.createFileMetadataCsv(consignmentData.files, customMetadata)
        ffidMetadataCsv <- bagAdditionalFiles.createFfidMetadataCsv(validatedFfidMetadata)
        antivirusCsv <- bagAdditionalFiles.createAntivirusMetadataCsv(validatedAntivirusMetadata)
        checksums <- ChecksumCalculator().calculateChecksums(fileMetadataCsv, ffidMetadataCsv, antivirusCsv)
        _ <- bagit.writeTagManifestRows(bag, checksums)
      } yield bagMetadata
    }

}

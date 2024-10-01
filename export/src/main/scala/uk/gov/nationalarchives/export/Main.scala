package uk.gov.nationalarchives.`export`

import cats.effect.{ExitCode, IO}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}
import uk.gov.nationalarchives.`export`.Arguments._
import uk.gov.nationalarchives.aws.utils.s3.S3Clients.s3
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionClients.sfnAsyncClient
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionUtils
import uk.gov.nationalarchives.consignmentexport.BuildInfo.version

import scala.concurrent.duration._

object Main extends CommandIOApp("tdr-export", "Exports tdr files with a flat structure", version = version) {

  case class Db(useIamAuth: Boolean, host: String, user: String, password: String, port: Int)
  case class S3(endpoint: String, cleanBucket: String, outputBucket: String, outputBucketJudgment: String)
  case class SFN(endpoint: String)
  case class SNS(endpoint: String, topicArn: String)
  case class Config(db: Db, sfn: SFN, s3: S3, sns: SNS)
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]
  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

  override def main: Opts[IO[ExitCode]] = exportOpts.map { case FileExport(consignmentId, taskToken) =>
    def runHeartbeat(stepFunction: StepFunction): IO[Unit] = stepFunction.sendHeartbeat(taskToken) >> IO.sleep(30.seconds) >> runHeartbeat(stepFunction)

    val exitCode = for {
      config <- ConfigSource.default.loadF[IO, Config]
      stepFunction = StepFunction(StepFunctionUtils(sfnAsyncClient(config.sfn.endpoint)))
      s3Utils = S3Utils(config, s3(config.s3.endpoint))
      publishUtils = PublishUtils(config)
      heartBeat <- runHeartbeat(stepFunction).start
      metadataUtils <- MetadataUtils(config)
      consignmentType <- metadataUtils.getConsignmentType(consignmentId)
      consignmentMetadata <- metadataUtils.getConsignmentMetadata(consignmentId)
      fileOutputs <- s3Utils.copyFiles(consignmentId, consignmentType, consignmentMetadata)
      fileMetadata <- metadataUtils.getFileMetadata(consignmentId)
      _ <- IO.raiseWhen(fileMetadata.isEmpty)(new RuntimeException(s"Metadata for consignment $consignmentId is missing"))
      ffidMetadata <- metadataUtils.getFFIDMetadata(consignmentId)
      _ <- s3Utils.putMetadata(consignmentType, fileOutputs.map(_.fileId), fileMetadata, consignmentMetadata, ffidMetadata)
      _ <- publishUtils.publishMessages(fileOutputs)
      _ <- stepFunction.publishSuccess(taskToken)
      _ <- heartBeat.cancel
    } yield ExitCode.Success

    exitCode.onError { err =>
      for {
        config <- ConfigSource.default.loadF[IO, Config]
        _ <- logger.error(err)(err.getMessage)
        _ <- StepFunction(StepFunctionUtils(sfnAsyncClient(config.sfn.endpoint))).publishFailure(taskToken, err.getMessage)
      } yield ()
    }
  }
}

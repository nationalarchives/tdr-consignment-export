package uk.gov.nationalarchives

import cats.effect.{ExitCode, IO}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import software.amazon.awssdk.services.sns.model.PublishResponse
import uk.gov.nationalarchives.Arguments._
import uk.gov.nationalarchives.aws.utils.sns.SNSUtils
import uk.gov.nationalarchives.aws.utils.sns.SNSClients._
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
      heartBeat <- runHeartbeat(stepFunction).start
      metadataUtils <- MetadataUtils(config)
      fileMetadata <- metadataUtils.getFileMetadata(consignmentId)
      ffidMetadata <- metadataUtils.getFFIDMetadata(consignmentId)
      consignmentMetadata <- metadataUtils.getConsignmentMetadata(consignmentId)
      consignmentType <- metadataUtils.getConsignmentType(consignmentId)
      fileOutputs <- s3Utils.copyFiles(consignmentId, consignmentType)
      _ <- s3Utils.createMetadata(consignmentType, fileOutputs.map(_.fileId), fileMetadata, consignmentMetadata, ffidMetadata)
      _ <- IO(fileOutputs.map(fileOutput => sendMessage(config, fileOutput)))
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

  private def sendMessage(config: Config, fileOutput: S3Utils.FileOutput): PublishResponse = {
    val snsUtils = SNSUtils(sns(config.sns.endpoint))
    snsUtils.publish(fileOutput.asJson.printWith(Printer.noSpaces), config.sns.topicArn)
    PublishResponse.builder().build()
  }
}

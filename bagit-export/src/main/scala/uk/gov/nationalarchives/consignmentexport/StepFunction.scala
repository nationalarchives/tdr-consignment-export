package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import io.circe.generic.auto._
import io.circe.syntax._
import org.typelevel.log4cats.SelfAwareStructuredLogger
import software.amazon.awssdk.services.sfn.model.{SendTaskFailureResponse, SendTaskSuccessResponse}
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionUtils
import uk.gov.nationalarchives.consignmentexport.StepFunction.ExportOutput

import java.util.UUID

class StepFunction(stepFunctionUtils: StepFunctionUtils)(implicit val logger: SelfAwareStructuredLogger[IO]) {

  def publishSuccess(taskToken: String, exportOutput: ExportOutput): IO[SendTaskSuccessResponse] =
    stepFunctionUtils.sendTaskSuccessRequest(taskToken, exportOutput.asJson)

  def publishFailure(taskToken: String, cause: String): IO[SendTaskFailureResponse] =
    stepFunctionUtils.sendTaskFailureRequest(taskToken, cause)

  def sendHeartbeat(taskToken: String): IO[Unit] =
    stepFunctionUtils.sendTaskHeartbeat(taskToken).attempt.flatMap {
      case Left(err) => logger.error(err)("Error sending the task heartbeat")
      case Right(_) => logger.info(s"Task heartbeat sent successfully")
    }
}

object StepFunction {

  def apply(stepFunctionUtils: StepFunctionUtils)
           (implicit logger: SelfAwareStructuredLogger[IO]): StepFunction = new StepFunction(stepFunctionUtils)(logger)

  case class ExportOutput(userId: UUID, consignmentReference: String, transferringBodyName: String, consignmentType: String, exportBucket: String)
}

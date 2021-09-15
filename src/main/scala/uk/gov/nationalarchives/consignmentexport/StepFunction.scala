package uk.gov.nationalarchives.consignmentexport

import java.util.UUID

import cats.effect.IO
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.circe.generic.auto._
import io.circe.syntax._
import software.amazon.awssdk.services.sfn.model.{SendTaskFailureResponse, SendTaskHeartbeatResponse, SendTaskSuccessResponse}
import uk.gov.nationalarchives.aws.utils.StepFunctionUtils
import uk.gov.nationalarchives.consignmentexport.StepFunction.ExportOutput

class StepFunction(stepFunctionUtils: StepFunctionUtils)(implicit val logger: SelfAwareStructuredLogger[IO]) {

  def publishSuccess(taskToken: String, exportOutput: ExportOutput): IO[SendTaskSuccessResponse] =
    stepFunctionUtils.sendTaskSuccessRequest(taskToken, exportOutput.asJson)

  def publishFailure(taskToken: String, cause: String): IO[SendTaskFailureResponse] =
    stepFunctionUtils.sendTaskFailureRequest(taskToken, cause)

  def sendHeartbeat(taskToken: String): IO[SendTaskHeartbeatResponse] =
    stepFunctionUtils.sendTaskHeartbeat(taskToken)
}

object StepFunction {

  def apply(stepFunctionUtils: StepFunctionUtils)
           (implicit logger: SelfAwareStructuredLogger[IO]): StepFunction = new StepFunction(stepFunctionUtils)(logger)

  case class ExportOutput(userId: UUID, consignmentReference: String, transferringBodyCode: String)
}

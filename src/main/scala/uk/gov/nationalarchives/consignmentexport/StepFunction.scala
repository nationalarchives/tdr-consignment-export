package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.circe.generic.auto._
import io.circe.syntax._
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessResponse
import uk.gov.nationalarchives.aws.utils.StepFunctionUtils
import uk.gov.nationalarchives.consignmentexport.StepFunction.ExportOutput

class StepFunction(stepFunctionUtils: StepFunctionUtils)(implicit val logger: SelfAwareStructuredLogger[IO]) {
  def publishSuccess(taskToken: String, exportOutput: ExportOutput): IO[SendTaskSuccessResponse] = for {
    result <- stepFunctionUtils.sendTaskSuccessRequest(taskToken, exportOutput.asJson)
  } yield result
}

object StepFunction {

  def apply(stepFunctionUtils: StepFunctionUtils)
           (implicit logger: SelfAwareStructuredLogger[IO]): StepFunction = new StepFunction(stepFunctionUtils)(logger)

  case class ExportOutput(userId: String)
}

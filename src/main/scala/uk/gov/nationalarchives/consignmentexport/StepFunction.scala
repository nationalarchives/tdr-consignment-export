package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.circe.generic.auto._
import io.circe.syntax._
import software.amazon.awssdk.services.sfn.model.SendTaskSuccessResponse
import uk.gov.nationalarchives.aws.utils.StepFunctionUtils
import uk.gov.nationalarchives.consignmentexport.StepFunction.ExportOutput

class StepFunction(stepFunctionUtils: StepFunctionUtils)(implicit val logger: SelfAwareStructuredLogger[IO]) {

  //If there is a taskToken value means Step Function requires response as using callback pattern
  //Temporarily make taskToken option to allow for deployment of code without disruption of service
  def publishSuccess(taskToken: Option[String], exportOutput: ExportOutput): IO[SendTaskSuccessResponse] =
    taskToken.map(tt => stepFunctionUtils.sendTaskSuccessRequest(tt, exportOutput.asJson))
      .getOrElse(IO(SendTaskSuccessResponse.builder.build()))
}

object StepFunction {

  def apply(stepFunctionUtils: StepFunctionUtils)
           (implicit logger: SelfAwareStructuredLogger[IO]): StepFunction = new StepFunction(stepFunctionUtils)(logger)

  case class ExportOutput(userId: String = "", consignmentReference: String = "", transferringBodyCode: String = "")
}

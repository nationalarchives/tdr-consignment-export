package uk.gov.nationalarchives.`export`

import cats.effect.IO
import io.circe.{Json, JsonObject}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import software.amazon.awssdk.services.sfn.model.{SendTaskFailureResponse, SendTaskSuccessResponse}
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionUtils

class StepFunction(stepFunctionUtils: StepFunctionUtils)(implicit val logger: SelfAwareStructuredLogger[IO]) {

  def publishSuccess(taskToken: String): IO[SendTaskSuccessResponse] =
    stepFunctionUtils.sendTaskSuccessRequest(taskToken, Json.fromJsonObject(JsonObject.empty))

  def publishFailure(taskToken: String, cause: String): IO[SendTaskFailureResponse] =
    stepFunctionUtils.sendTaskFailureRequest(taskToken, cause)

  def sendHeartbeat(taskToken: String): IO[Unit] =
    stepFunctionUtils.sendTaskHeartbeat(taskToken).attempt.flatMap {
      case Left(err) => logger.error(err)("Error sending the task heartbeat")
      case Right(_)  => logger.info(s"Task heartbeat sent successfully")
    }
}

object StepFunction {

  def apply(stepFunctionUtils: StepFunctionUtils)(implicit logger: SelfAwareStructuredLogger[IO]): StepFunction = new StepFunction(stepFunctionUtils)(logger)
}

package uk.gov.nationalarchives.consignmentexport

import java.util.UUID

import cats.effect.IO
import io.circe.Encoder.AsObject.importedAsObjectEncoder
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import org.mockito.ArgumentCaptor
import software.amazon.awssdk.services.sfn.model.{SendTaskFailureResponse, SendTaskSuccessResponse}
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionUtils
import uk.gov.nationalarchives.consignmentexport.StepFunction.ExportOutput
import cats.effect.unsafe.implicits.global

class StepFunctionSpec extends ExportSpec {

  "the publishSuccess method" should "call the library method with the correct arguments" in {
    val sfnUtils = mock[StepFunctionUtils]
    val taskTokenCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val exportOutputCaptor: ArgumentCaptor[Json] = ArgumentCaptor.forClass(classOf[Json])
    val mockResponse = IO.pure(SendTaskSuccessResponse.builder.build)

    doAnswer(() => mockResponse).when(sfnUtils).sendTaskSuccessRequest(taskTokenCaptor.capture(), exportOutputCaptor.capture())

    val taskToken = "taskToken1234"
    val exportOutput = ExportOutput(UUID.randomUUID(), "consignmentReference", "tb-name", "series-id","standard", "judgments3ExportBucket")

    StepFunction(sfnUtils).publishSuccess(taskToken, exportOutput).unsafeRunSync()
    taskTokenCaptor.getValue should equal(taskToken)
    exportOutputCaptor.getValue should equal(exportOutput.asJson)
  }

  "the publishFailure method" should "call the library method with the correct arguments" in {
    val sfnUtils = mock[StepFunctionUtils]
    val taskTokenCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val errorCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val mockResponse = IO.pure(SendTaskFailureResponse.builder.build)

    doAnswer(() => mockResponse).when(sfnUtils).sendTaskFailureRequest(taskTokenCaptor.capture(), errorCaptor.capture())

    val taskToken = "taskToken1234"
    val error = "some error message"

    StepFunction(sfnUtils).publishFailure(taskToken, error).unsafeRunSync()
    taskTokenCaptor.getValue should equal(taskToken)
    errorCaptor.getValue should equal(error)
  }
}

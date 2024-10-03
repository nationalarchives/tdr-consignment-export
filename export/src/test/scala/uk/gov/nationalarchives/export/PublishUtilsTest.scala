package uk.gov.nationalarchives.`export`

import cats.effect.testkit.TestControl
import cats.effect.unsafe.implicits.global
import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import software.amazon.awssdk.http.SdkHttpFullResponse
import software.amazon.awssdk.services.sns.model.PublishResponse
import uk.gov.nationalarchives.`export`.Main.{Config, Db, S3, SFN, SNS}
import uk.gov.nationalarchives.`export`.S3Utils.FileOutput
import uk.gov.nationalarchives.aws.utils.sns.SNSUtils

import java.util.UUID

class PublishUtilsTest extends AnyFlatSpec with MockitoSugar {

  "publishMessages" should "retry any failed messages" in {
    val config: Config = Config(Db(useIamAuth = false, "", "", "", 5432), SFN(""), S3("", "", "", ""), SNS("", "testTopic", 500))
    val snsUtils = mock[SNSUtils]
    def response(statusCode: Int): PublishResponse = PublishResponse.builder.sdkHttpResponse(SdkHttpFullResponse.builder.statusCode(statusCode).build).build().asInstanceOf[PublishResponse]

    when(snsUtils.publish(any[String], any[String]))
      .thenReturn(response(400), List.fill(10)(response(400)) ++ List(response(200)):_*)
    val outputs = (1 to 600).map(_ => FileOutput("", UUID.randomUUID, None, None)).toList

    val fileOutputs = TestControl.executeEmbed(new PublishUtils(snsUtils, config).publishMessages(outputs))

    fileOutputs.unsafeRunSync().size should equal(611)
  }
}

package uk.gov.nationalarchives.`export`

import cats.effect.{IO, Temporal}
import io.circe.Printer
import uk.gov.nationalarchives.`export`.Main.Config
import uk.gov.nationalarchives.`export`.S3Utils.FileOutput
import uk.gov.nationalarchives.aws.utils.sns.SNSClients.sns
import uk.gov.nationalarchives.aws.utils.sns.SNSUtils
import io.circe.syntax._
import io.circe.generic.auto._
import cats.syntax.all._

import scala.concurrent.duration._

class PublishUtils(snsUtils: SNSUtils, config: Config) {

  def publishMessages(fileOutputs: List[FileOutput]): IO[List[FileOutput]] = {
    def processGroup(eachGroup: List[FileOutput]): IO[List[FileOutput]] = {
      val groupResponses = eachGroup.flatMap { fileOutput =>
        val publishResponse = snsUtils.publish(fileOutput.asJson.printWith(Printer.noSpaces), config.sns.topicArn)
        if (publishResponse.sdkHttpResponse().isSuccessful) None else Option(fileOutput)
      }
      if (groupResponses.isEmpty) {
        IO.pure(fileOutputs)
      } else {
        publishMessages(groupResponses)
      }
    }

    fileOutputs.grouped(500).toList.flatTraverse { eachGroup =>
      for {
        results <- processGroup(eachGroup)
        _ <- Temporal[IO].sleep(1.seconds)
      } yield results
    }
  }
}
object PublishUtils {
  def apply(config: Config): PublishUtils = new PublishUtils(SNSUtils(sns(config.sns.endpoint)), config)
}

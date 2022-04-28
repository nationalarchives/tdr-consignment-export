package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigSource}

object Config {

  case class S3(endpoint: String, cleanBucket: String, outputBucket: String, outputBucketJudgment: String,
                downloadFilesBatchSize: Int, downloadBatchDelayMs: Int)
  case class Api(url: String)
  case class Auth(url: String, clientId: String, clientSecret: String, realm: String)
  case class EFS(rootLocation: String)
  case class StepFunction(endpoint: String)
  case class Configuration(s3: S3, api: Api, auth: Auth, efs: EFS, stepFunction: StepFunction)

  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))

  def config(): IO[Configuration] = ConfigSource.default.loadF[IO, Configuration]
}

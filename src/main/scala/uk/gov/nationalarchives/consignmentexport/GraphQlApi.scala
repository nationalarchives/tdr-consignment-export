package uk.gov.nationalarchives.consignmentexport

import java.time.ZonedDateTime
import java.util.UUID

import cats.effect.{ContextShift, IO}
import cats.implicits._
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gce}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment}
import graphql.codegen.UpdateExportLocation.{updateExportLocation => uel}
import graphql.codegen.types.UpdateExportLocationInput
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}
import GraphQlApi._
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.consignmentexport.Config.Configuration

import scala.concurrent.{ExecutionContextExecutor, Future}

class GraphQlApi(keycloak: KeycloakUtils,
                 consignmentClient: GraphQLClient[gce.Data, gce.Variables],
                 updateExportLocationClient: GraphQLClient[uel.Data, uel.Variables])(
  implicit val contextShift: ContextShift[IO],
  val logger: SelfAwareStructuredLogger[IO],
  keycloakDeployment: TdrKeycloakDeployment,
  backend: SttpBackend[Identity, Nothing, NothingT]) {

  implicit class ErrorUtils[D](response: GraphQlResponse[D]) {
    val errorString: String = response.errors.map(_.message).mkString("\n")
  }

  def getConsignmentMetadata(config: Configuration, consignmentId: UUID): IO[Option[gce.GetConsignment]] = for {
    token <- keycloak.serviceAccountToken(config.auth.clientId, config.auth.clientSecret).toIO
    exportResult <- consignmentClient.getResult(token, gce.document, gce.Variables(consignmentId).some).toIO
    consignmentData <-
      IO.fromOption(exportResult.data)(new RuntimeException(s"No consignment found for consignment id $consignmentId ${exportResult.errorString}"))
    consignment = consignmentData.getConsignment
  } yield consignment

  def updateExportLocation(config: Configuration, consignmentId: UUID, tarPath: String, exportDatetime: ZonedDateTime): IO[Option[Int]] = for {
    token <- keycloak.serviceAccountToken(config.auth.clientId, config.auth.clientSecret).toIO
    response <- updateExportLocationClient.getResult(token, uel.document, uel.Variables(UpdateExportLocationInput(consignmentId, tarPath, Some(exportDatetime))).some).toIO
    data <- IO.fromOption(response.data)(new RuntimeException(s"No data returned from the update export call for consignment $consignmentId ${response.errorString}"))
    _ <- logger.info(s"Export location updated for consignment $consignmentId")
  } yield data.updateExportLocation

}

object GraphQlApi {
  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  def apply(apiUrl: String)(
    implicit contextShift: ContextShift[IO],
    logger: SelfAwareStructuredLogger[IO],
    keycloakDeployment: TdrKeycloakDeployment,
    backend: SttpBackend[Identity, Nothing, NothingT]
  ): GraphQlApi = {
    val keycloak = new KeycloakUtils()
    val getConsignmentClient = new GraphQLClient[gce.Data, gce.Variables](apiUrl)
    val updateExportLocationClient = new GraphQLClient[uel.Data, uel.Variables](apiUrl)
    new GraphQlApi(keycloak, getConsignmentClient, updateExportLocationClient)(contextShift, logger, keycloakDeployment, backend)
  }

  implicit class FutureUtils[T](f: Future[T])(implicit contextShift: ContextShift[IO]) {
    def toIO: IO[T] = IO.fromFuture(IO(f))
  }
}

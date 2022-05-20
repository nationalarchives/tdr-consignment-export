package uk.gov.nationalarchives.consignmentexport

import java.time.ZonedDateTime
import java.util.UUID
import cats.implicits._
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gce}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment}
import graphql.codegen.UpdateExportData.{updateExportData => ued}
import graphql.codegen.types.{ConsignmentStatusInput, UpdateExportDataInput}
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import GraphQlApi._
import cats.effect.IO
import graphql.codegen.UpdateConsignmentStatus.{updateConsignmentStatus => ucs}
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.consignmentexport.Config.Configuration

import scala.concurrent.{ExecutionContextExecutor, Future}

class GraphQlApi(keycloak: KeycloakUtils,
                 consignmentClient: GraphQLClient[gce.Data, gce.Variables],
                 updateExportDataClient: GraphQLClient[ued.Data, ued.Variables],
                 updateConsignmentClient: GraphQLClient[ucs.Data, ucs.Variables])(
                  implicit val logger: SelfAwareStructuredLogger[IO],
                  keycloakDeployment: TdrKeycloakDeployment,
                  backend: SttpBackend[Identity, Any]) {

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

  def updateConsignmentStatus(config: Configuration, consignmentId: UUID, statusType: String, status: String): IO[Option[Int]] = for {
    token <- keycloak.serviceAccountToken(config.auth.clientId, config.auth.clientSecret).toIO
    exportResult <- updateConsignmentClient.getResult(token, ucs.document, ucs.Variables(ConsignmentStatusInput(consignmentId, statusType, status)).some).toIO
    consignmentStatus <-
      IO.fromOption(exportResult.data)(new RuntimeException(s"No consignment found for the update consignment call for consignment $consignmentId ${exportResult.errorString}"))
    updateConsignmentStatus = consignmentStatus.updateConsignmentStatus
    _ <- logger.info(s"Export data completed for consignment $consignmentId")
  } yield updateConsignmentStatus

  def updateExportData(config: Configuration, consignmentId: UUID, tarPath: String, exportDatetime: ZonedDateTime, exportVersion: String): IO[Option[Int]] = for {
    token <- keycloak.serviceAccountToken(config.auth.clientId, config.auth.clientSecret).toIO
    response <- updateExportDataClient.getResult(token, ued.document, ued.Variables(UpdateExportDataInput(consignmentId, tarPath, Some(exportDatetime), exportVersion)).some).toIO
    data <- IO.fromOption(response.data)(new RuntimeException(s"No data returned from the update export call for consignment $consignmentId ${response.errorString}"))
    _ <- logger.info(s"Export data updated for consignment $consignmentId")
  } yield data.updateExportData
}

object GraphQlApi {
  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  def apply(apiUrl: String)(
    implicit logger: SelfAwareStructuredLogger[IO],
    keycloakDeployment: TdrKeycloakDeployment,
    backend: SttpBackend[Identity, Any]
  ): GraphQlApi = {
    val keycloak = new KeycloakUtils()
    val getConsignmentClient = new GraphQLClient[gce.Data, gce.Variables](apiUrl)
    val updateExportDataClient = new GraphQLClient[ued.Data, ued.Variables](apiUrl)
    val updateConsignmentStatus = new GraphQLClient[ucs.Data, ucs.Variables](apiUrl)
    new GraphQlApi(keycloak, getConsignmentClient, updateExportDataClient, updateConsignmentStatus)(logger, keycloakDeployment, backend)
  }

  implicit class FutureUtils[T](f: Future[T]) {
    def toIO: IO[T] = IO.fromFuture(IO(f))
  }
}

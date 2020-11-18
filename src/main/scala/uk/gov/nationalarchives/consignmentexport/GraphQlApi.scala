package uk.gov.nationalarchives.consignmentexport

import java.util.UUID

import cats.effect.{ContextShift, IO}
import cats.implicits._
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import graphql.codegen.GetFiles.{getFiles => gf}
import graphql.codegen.UpdateExportLocation.{updateExportLocation => uel}
import graphql.codegen.GetOriginalPath.{getOriginalPath => gop}
import graphql.codegen.types.UpdateExportLocationInput
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}
import GraphQlApi._
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.consignmentexport.Config.Configuration

import scala.concurrent.{ExecutionContextExecutor, Future}

class GraphQlApi(keycloak: KeycloakUtils,
                 filesClient: GraphQLClient[gf.Data, gf.Variables],
                 updateExportLocationClient: GraphQLClient[uel.Data, uel.Variables],
                 getOriginalPathClient: GraphQLClient[gop.Data, gop.Variables])(implicit val contextShift: ContextShift[IO], val logger: SelfAwareStructuredLogger[IO]) {

  def getFiles(config: Configuration, consignmentId: UUID): IO[List[FileIdWithPath]] = for {
    token <- keycloak.serviceAccountToken(config.auth.clientId, config.auth.clientSecret).toIO
    filesResult <- filesClient.getResult(token, gf.document, gf.Variables(consignmentId).some).toIO
    data <- IO.fromOption(filesResult.data)(new RuntimeException(s"No files found for consignment $consignmentId"))
    originalPath <- data.getFiles.fileIds.map(fileId => getOriginalPath(config, fileId)).sequence
  } yield originalPath

  def updateExportLocation(config: Configuration, consignmentId: UUID, tarPath: String): IO[Option[Int]] = for {
    token <- keycloak.serviceAccountToken(config.auth.clientId, config.auth.clientSecret).toIO
    response <- updateExportLocationClient.getResult(token, uel.document, uel.Variables(UpdateExportLocationInput(consignmentId, tarPath)).some).toIO
    data <- IO.fromOption(response.data)(new RuntimeException(s"No data returned from the update export call for consignment $consignmentId"))
    _ <- logger.info(s"Export location updated for consignment $consignmentId")
  } yield data.updateExportLocation

  def getOriginalPath(config: Configuration, fileId: UUID) = for {
    token <- keycloak.serviceAccountToken(config.auth.clientId, config.auth.clientSecret).toIO
    response <- getOriginalPathClient.getResult(token, gop.document, gop.Variables(fileId).some).toIO
    data <- IO.fromOption(response.data)(new RuntimeException(s"No data returned from the original path call for file id $fileId"))
    originalPath <- IO.fromOption(data.getClientFileMetadata.originalPath)(new RuntimeException("The original path is missing or empty"))
  } yield FileIdWithPath(fileId, originalPath)
}

object GraphQlApi {
  implicit val ec: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  def apply(apiUrl: String, authUrl: String)(implicit contextShift: ContextShift[IO], logger: SelfAwareStructuredLogger[IO]): GraphQlApi = {
    val keycloak = new KeycloakUtils(authUrl)
    val getFilesClient = new GraphQLClient[gf.Data, gf.Variables](apiUrl)
    val updateExportLocationClient = new GraphQLClient[uel.Data, uel.Variables](apiUrl)
    val getOriginalPathClient = new GraphQLClient[gop.Data, gop.Variables](apiUrl)
    new GraphQlApi(keycloak, getFilesClient, updateExportLocationClient, getOriginalPathClient)(contextShift, logger)
  }

  implicit class FutureUtils[T](f: Future[T])(implicit contextShift: ContextShift[IO]) {
    def toIO: IO[T] = IO.fromFuture(IO(f))
  }

  implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  case class FileIdWithPath(fileId: UUID, originalPath: String)
}

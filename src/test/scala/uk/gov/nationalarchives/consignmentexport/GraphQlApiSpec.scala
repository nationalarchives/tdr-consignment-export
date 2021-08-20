package uk.gov.nationalarchives.consignmentexport

import java.time.{LocalDateTime, ZonedDateTime}
import java.util.UUID

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.GetConsignmentExport
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.Metadata
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.{Files, Series, TransferringBody}
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gce}
import graphql.codegen.UpdateExportLocation.{updateExportLocation => uel}
import sangria.ast.Document
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}
import uk.gov.nationalarchives.consignmentexport.Config._
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag

class GraphQlApiSpec extends ExportSpec {
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
  implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  private val fixedDateTime = ZonedDateTime.now()

  val config = Configuration(
    S3("", "", ""),
    Api(""),
    Auth("authUrl", "clientId", "clientSecret", "realm"),
    EFS(""),
    StepFunction(""))

  "the updateExportLocation method" should "return the correct value" in {
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[uel.Data, uel.Variables]]
    val keycloak = mock[KeycloakUtils]
    val api = new GraphQlApi(keycloak, consignmentClient, updateExportClient)
    val consignmentId = UUID.randomUUID()

    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])
    val data = new GraphQlResponse[uel.Data](uel.Data(1.some).some, List())
    doAnswer(() => Future(data)).when(updateExportClient).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[uel.Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])

    val response = api.updateExportLocation(config, consignmentId, s"s3://testbucket/$consignmentId.tar.gz", fixedDateTime).unsafeRunSync()
    response.isDefined should be(true)
    response.get should equal(1)
  }

  "the updateExportLocation method" should "throw an exception if no data is returned" in {
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[uel.Data, uel.Variables]]
    val keycloak = mock[KeycloakUtils]
    val api = new GraphQlApi(keycloak, consignmentClient, updateExportClient)
    val consignmentId = UUID.randomUUID()

    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])
    val data = new GraphQlResponse[uel.Data](Option.empty[uel.Data], List())
    doAnswer(() => Future(data)).when(updateExportClient).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[uel.Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])

    val exception = intercept[RuntimeException] {
      api.updateExportLocation(config, consignmentId, s"s3://testbucket/$consignmentId.tar.gz", fixedDateTime).unsafeRunSync()
    }
    exception.getMessage should equal(s"No data returned from the update export call for consignment $consignmentId ")
  }

  "the getConsignmentMetadata method" should "return the correct value" in {
    val fixedDate = ZonedDateTime.now()
    val userId = UUID.randomUUID()
    val consignmentRef = "consignmentReference-1234"
    val series = Series("series-code")
    val transferringBody = TransferringBody("tb-code")
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[uel.Data, uel.Variables]]
    val keycloak = mock[KeycloakUtils]
    val api = new GraphQlApi(keycloak, consignmentClient, updateExportClient)
    val consignmentId = UUID.randomUUID()
    val fileId = UUID.randomUUID()
    val lastModified = LocalDateTime.now().some
    val fileMetadata = Metadata(1L.some, lastModified, "clientSideOriginalFilePath".some, "foiExemptionCode".some, "heldBy".some, "language".some, "legalStatus".some, "rightsCopyright".some, "clientSideChecksum".some)
    val consignment = GetConsignmentExport.getConsignmentForExport.GetConsignment(
      userId, Some(fixedDate), Some(fixedDate), Some(fixedDate), consignmentRef, Some(series), Some(transferringBody), List(Files(fileId, fileMetadata, Option.empty, Option.empty))
    )

    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])
    val data = new GraphQlResponse[gce.Data](gce.Data(Some(consignment)).some, List())
    doAnswer(() => Future(data)).when(consignmentClient).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[gce.Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])

    val response = api.getConsignmentMetadata(config, consignmentId).unsafeRunSync()
    response.get.userid should be(userId)
    response.get.createdDatetime should be(Some(fixedDate))
    response.get.transferInitiatedDatetime should be(Some(fixedDate))
    response.get.exportDatetime should be(Some(fixedDate))
    response.get.series should be(Some(series))
    response.get.transferringBody should be(Some(transferringBody))
    response.get.consignmentReference should be(consignmentRef)
  }

  "the getConsignmentMetadata method" should "throw an exception if no data is returned" in {
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[uel.Data, uel.Variables]]
    val keycloak = mock[KeycloakUtils]
    val api = new GraphQlApi(keycloak, consignmentClient, updateExportClient)
    val consignmentId = UUID.randomUUID()

    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](any[String], any[String])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])
    val data = new GraphQlResponse[gce.Data](Option.empty[gce.Data], List())
    doAnswer(() => Future(data)).when(consignmentClient).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[gce.Variables]])(any[SttpBackend[Identity, Nothing, NothingT]], any[ClassTag[Identity[_]]])

    val exception = intercept[RuntimeException] {
      api.getConsignmentMetadata(config, consignmentId).unsafeRunSync()
    }
    exception.getMessage should equal(s"No consignment found for consignment id $consignmentId ")
  }
}

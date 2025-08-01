package uk.gov.nationalarchives.consignmentexport

import java.time.{LocalDateTime, ZonedDateTime}
import java.util.UUID
import cats.implicits._
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.GetConsignmentExport
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gce}
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.UpdateExportData.{updateExportData => ued}
import graphql.codegen.UpdateConsignmentStatus.{updateConsignmentStatus => ucs}
import sangria.ast.Document
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.consignmentexport.Config._
import uk.gov.nationalarchives.consignmentexport.ExportSpec.customMetadata
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment}
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.reflect.ClassTag
import cats.effect.unsafe.implicits.global
import uk.gov.nationalarchives.consignmentexport.ConsignmentStatus.{StatusType, StatusValue}

class GraphQlApiSpec extends ExportSpec {
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()

  private val fixedDateTime = ZonedDateTime.now()

  val config: Configuration = Configuration(
    S3("", "", "", "", 0, 0),
    Api(""),
    Auth("authUrl", "clientId", "clientSecret", "realm"),
    EFS(""),
    StepFunction(""),
    ConsignmentTypeOverride(List(), List())
  )

  implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment("authUrl", "realm", 3600)

  "the updateExportData method" should "return the correct value" in {
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[ued.Data, ued.Variables]]
    val updateConsignmentStatus = mock[GraphQLClient[ucs.Data, ucs.Variables]]
    val customMetadataClient: GraphQLClient[cm.Data, cm.Variables] = mock[GraphQLClient[cm.Data, cm.Variables]]
    val keycloak = mock[KeycloakUtils]
    val consignmentId = UUID.randomUUID()
    val api = new GraphQlApi(config, consignmentId, keycloak, consignmentClient, updateExportClient, updateConsignmentStatus, customMetadataClient)

    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](
      any[String], any[String])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])
    val data = new GraphQlResponse[ued.Data](ued.Data(1.some).some, List())
    doAnswer(() => Future(data)).when(updateExportClient).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[ued.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])

    val response = api.updateExportData(s"s3://testbucket/$consignmentId.tar.gz", fixedDateTime, "0.0.Version").unsafeRunSync()
    response.isDefined should be(true)
    response.get should equal(1)
  }

  "the updateExportData method" should "throw an exception if no data is returned" in {
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[ued.Data, ued.Variables]]
    val updateConsignmentStatus = mock[GraphQLClient[ucs.Data, ucs.Variables]]
    val customMetadataClient: GraphQLClient[cm.Data, cm.Variables] = mock[GraphQLClient[cm.Data, cm.Variables]]
    val keycloak = mock[KeycloakUtils]
    val consignmentId = UUID.randomUUID()
    val api = new GraphQlApi(config, consignmentId, keycloak, consignmentClient, updateExportClient, updateConsignmentStatus, customMetadataClient)
    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])
    val data = new GraphQlResponse[ued.Data](Option.empty[ued.Data], List())
    doAnswer(() => Future(data)).when(updateExportClient).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[ued.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])

    val exception = intercept[RuntimeException] {
      api.updateExportData(s"s3://testbucket/$consignmentId.tar.gz", fixedDateTime, "0.0.Version").unsafeRunSync()
    }
    exception.getMessage should equal(s"No data returned from the update export call for consignment $consignmentId ")
  }

  "the getConsignmentMetadata method" should "return the correct value" in {
    val fixedDate = ZonedDateTime.now()
    val userId = UUID.randomUUID()
    val consignmentRef = "consignmentReference-1234"
    val series = "series-code"
    val transferringBody = "tb-name"
    val consignmentType = "standard"
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[ued.Data, ued.Variables]]
    val updateConsignmentStatus = mock[GraphQLClient[ucs.Data, ucs.Variables]]
    val customMetadataClient: GraphQLClient[cm.Data, cm.Variables] = mock[GraphQLClient[cm.Data, cm.Variables]]
    val keycloak = mock[KeycloakUtils]
    val consignmentId = UUID.randomUUID()
    val api = new GraphQlApi(config, consignmentId, keycloak, consignmentClient, updateExportClient, updateConsignmentStatus, customMetadataClient)
    val fileId = UUID.randomUUID()
    val lastModified = LocalDateTime.now()
    val fileMetadata = createMetadata(lastModified, "clientSideOriginalFilePath", "clientSideChecksum")
    val originalFilePath = "/originalFilePath".some
    val metadataSchemaLibraryVersion = "Schema-Library-Version-v0.1".some
    val consignment = GetConsignmentExport.getConsignmentForExport.GetConsignment(
      userId, Some(fixedDate), Some(fixedDate), Some(fixedDate), consignmentRef, Some(consignmentType), None, Some(series), Some(transferringBody), List(Files(fileId, "File".some, "name".some, None, None, originalFilePath, fileMetadata, Option.empty, Option.empty)),metadataSchemaLibraryVersion
    )

    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])
    val data = new GraphQlResponse[gce.Data](gce.Data(Some(consignment)).some, List())
    doAnswer(() => Future(data)).when(consignmentClient).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[gce.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])

    val response = api.getConsignmentMetadata.unsafeRunSync()
    response.get.userid should be(userId)
    response.get.createdDatetime should be(Some(fixedDate))
    response.get.transferInitiatedDatetime should be(Some(fixedDate))
    response.get.exportDatetime should be(Some(fixedDate))
    response.get.seriesName should be(Some(series))
    response.get.transferringBodyName should be(Some(transferringBody))
    response.get.consignmentReference should be(consignmentRef)
    response.get.files.head.originalFilePath should be(originalFilePath)
  }

  "the getConsignmentMetadata method" should "throw an exception if no data is returned" in {
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[ued.Data, ued.Variables]]
    val updateConsignmentStatus = mock[GraphQLClient[ucs.Data, ucs.Variables]]
    val customMetadataClient: GraphQLClient[cm.Data, cm.Variables] = mock[GraphQLClient[cm.Data, cm.Variables]]
    val keycloak = mock[KeycloakUtils]
    val consignmentId = UUID.randomUUID()
    val api = new GraphQlApi(config, consignmentId, keycloak, consignmentClient, updateExportClient, updateConsignmentStatus, customMetadataClient)

    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])
    val data = new GraphQlResponse[gce.Data](Option.empty[gce.Data], List())
    doAnswer(() => Future(data)).when(consignmentClient).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[gce.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])

    val exception = intercept[RuntimeException] {
      api.getConsignmentMetadata.unsafeRunSync()
    }
    exception.getMessage should equal(s"No consignment found for consignment id $consignmentId ")
  }

  "the updateConsignmentStatus method" should "update consignment status" in {
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[ued.Data, ued.Variables]]
    val updateConsignmentStatus = mock[GraphQLClient[ucs.Data, ucs.Variables]]
    val customMetadataClient: GraphQLClient[cm.Data, cm.Variables] = mock[GraphQLClient[cm.Data, cm.Variables]]
    val keycloak = mock[KeycloakUtils]
    val consignmentId = UUID.randomUUID()
    val api = new GraphQlApi(config, consignmentId, keycloak, consignmentClient, updateExportClient, updateConsignmentStatus, customMetadataClient)

    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])
    val data = new GraphQlResponse[ucs.Data](ucs.Data(Some(1)).some, List())
    doAnswer(() => Future(data)).when(updateConsignmentStatus).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[ucs.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])

    val response = api.updateConsignmentStatus(StatusType.`export`, StatusValue.completed).unsafeRunSync()
    response.isDefined should be(true)
    response.get should be(1)
  }

  "the updateConsignmentStatus method" should "throw an exception if no data is returned" in {
    val consignmentClient = mock[GraphQLClient[gce.Data, gce.Variables]]
    val updateExportClient = mock[GraphQLClient[ued.Data, ued.Variables]]
    val updateConsignmentStatus = mock[GraphQLClient[ucs.Data, ucs.Variables]]
    val customMetadataClient: GraphQLClient[cm.Data, cm.Variables] = mock[GraphQLClient[cm.Data, cm.Variables]]
    val keycloak = mock[KeycloakUtils]
    val consignmentId = UUID.randomUUID()
    val api = new GraphQlApi(config, consignmentId, keycloak, consignmentClient, updateExportClient, updateConsignmentStatus, customMetadataClient)

    doAnswer(() => Future(new BearerAccessToken("token"))).when(keycloak).serviceAccountToken[Identity](any[String], any[String])(
      any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]], any[TdrKeycloakDeployment])
    val data = new GraphQlResponse[ucs.Data](Option.empty[ucs.Data], List())
    doAnswer(() => Future(data)).when(updateConsignmentStatus).getResult[Identity](any[BearerAccessToken], any[Document], any[Option[ucs.Variables]])(any[SttpBackend[Identity, Any]], any[ClassTag[Identity[_]]])

    val exception = intercept[RuntimeException] {
      api.updateConsignmentStatus(StatusType.`export`, StatusValue.completed).unsafeRunSync()
    }
    exception.getMessage should equal(s"No data returned from the update consignment status call for consignment $consignmentId ")
  }
}

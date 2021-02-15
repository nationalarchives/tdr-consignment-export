package uk.gov.nationalarchives.consignmentexport

import java.io.File
import java.net.URI
import java.nio.file.Path

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.typesafe.config.{Config, ConfigFactory}
import io.findify.s3mock.S3Mock
import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.{Keycloak, KeycloakBuilder}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model._
import software.amazon.awssdk.services.sfn.SfnAsyncClient

import scala.concurrent.ExecutionContext
import scala.io.Source.fromResource
import scala.jdk.CollectionConverters._
import scala.sys.process._

class ExternalServiceSpec extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures with Matchers {

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  private val appConfig: Config = ConfigFactory.load()
  val scratchDirectory: String = appConfig.getString("efs.rootLocation")

  val s3Client: S3Client = S3Client.builder
    .region(Region.EU_WEST_2)
    .endpointOverride(URI.create("http://localhost:8003/"))
    .build()

  val keycloakAdminClient = KeycloakBuilder.builder()
    .serverUrl("http://localhost:9002/auth")
    .realm("tdr")
    .clientId("tdr-backend-checks")
    .clientSecret("client-secret")
    .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
    .build()

  val sfnClient = SfnAsyncClient.builder
    .region(Region.EU_WEST_2)
    .endpointOverride(URI.create("http://localhost:9003/"))
    .build

  def createBucket(bucket: String): CreateBucketResponse = s3Client.createBucket(CreateBucketRequest.builder.bucket(bucket).build)

  def deleteBucket(bucket: String): DeleteBucketResponse = s3Client.deleteBucket(DeleteBucketRequest.builder.bucket(bucket).build)

  def outputBucketObjects(): List[S3Object] = s3Client.listObjects(ListObjectsRequest.builder.bucket("test-output-bucket").build)
    .contents().asScala.toList

  def getObject(key: String, path: Path): GetObjectResponse =
    s3Client.getObject(GetObjectRequest.builder.bucket("test-output-bucket").key(key).build, path)

  def putFile(key: String): PutObjectResponse = {
    val path = new File(getClass.getResource(s"/testfiles/testfile").getPath).toPath
    val putObjectRequest = PutObjectRequest.builder.bucket("test-clean-bucket").key(key).build
    s3Client.putObject(putObjectRequest, path)
  }

  val wiremockGraphqlServer = new WireMockServer(9001)
  val wiremockAuthServer = new WireMockServer(9002)
  val wiremockSfnServer = new WireMockServer(9003)

  implicit val ec: ExecutionContext = ExecutionContext.global

  val keycloakUserId = "b2657adf-6e93-424f-b0f1-aadd26762a96"

  val graphQlPath = "/graphql"
  val authPath = "/auth/realms/tdr/protocol/openid-connect/token"
  val keycloakGetRealmPath = "/auth/admin/realms/tdr"
  val keycloakGetUserPath = "/auth/admin/realms/tdr/users" + s"/$keycloakUserId"
  val sfnPublishSuccessPath = "/"

  def graphQlUrl: String = wiremockGraphqlServer.url(graphQlPath)

  def graphQlGetConsignmentMetadata: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
    .withRequestBody(equalToJson("{\"query\":\"query getConsignmentForExport($consignmentId:UUID!){getConsignment(consignmentid:$consignmentId){userid createdDatetime transferInitiatedDatetime exportDatetime series{code} transferringBody{code}}}\",\"variables\":{\"consignmentId\":\"50df01e6-2e5e-4269-97e7-531a755b417d\"}}"))
    .willReturn(okJson(fromResource(s"json/get_consignment_for_export.json").mkString)))

  def graphQlGetDifferentConsignmentMetadata: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
    .withRequestBody(equalToJson("{\"query\":\"query getConsignmentForExport($consignmentId:UUID!){getConsignment(consignmentid:$consignmentId){userid createdDatetime transferInitiatedDatetime exportDatetime series{code} transferringBody{code}}}\",\"variables\":{\"consignmentId\":\"6794231c-39fe-41e0-a498-b6a077563282\"}}"))
    .willReturn(okJson(fromResource(s"json/get_consignment_for_export.json").mkString)))

  def graphqlGetFiles: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
    .withRequestBody(equalToJson("{\"query\":\"query getFiles($consignmentId:UUID!){getFiles(consignmentid:$consignmentId){fileIds}}\",\"variables\":{\"consignmentId\":\"50df01e6-2e5e-4269-97e7-531a755b417d\"}}"))
    .willReturn(okJson(fromResource(s"json/get_files.json").mkString)))

  def graphqlGetEmptyFiles: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
    .withRequestBody(equalToJson("{\"query\":\"query getFiles($consignmentId:UUID!){getFiles(consignmentid:$consignmentId){fileIds}}\",\"variables\":{\"consignmentId\":\"6794231c-39fe-41e0-a498-b6a077563282\"}}"))
    .willReturn(okJson(fromResource(s"json/get_files_empty.json").mkString)))

  def graphqlUpdateExportLocation: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
    .willReturn(okJson(fromResource(s"json/get_files.json").mkString)))

  def graphqlGetOriginalPath: StubMapping = wiremockGraphqlServer.stubFor(post(urlEqualTo(graphQlPath))
    .withRequestBody(equalToJson("{\"query\":\"query getOriginalPath($fileId:UUID!){getClientFileMetadata(fileId:$fileId){originalPath}}\",\"variables\":{\"fileId\":\"7b19b272-d4d1-4d77-bf25-511dc6489d12\"}}"))
    .willReturn(okJson(fromResource("json/get_original_path.json").mkString))
  )

  def authOk: StubMapping = wiremockAuthServer.stubFor(post(urlEqualTo(authPath))
    .willReturn(okJson(fromResource(s"json/access_token.json").mkString)))

  def keycloakCreateAdminClient: Keycloak = keycloakAdminClient

  def keycloakGetUser: StubMapping = wiremockAuthServer.stubFor(get(urlEqualTo(keycloakGetUserPath))
    .willReturn(okJson(fromResource(s"json/get_keycloak_user.json").mkString)))

  def keycloakGetIncompleteUser: StubMapping = wiremockAuthServer.stubFor(get(urlEqualTo(keycloakGetUserPath))
    .willReturn(okJson(fromResource(s"json/get_incomplete_keycloak_user.json").mkString)))

  def sfnPublishSuccess: StubMapping = wiremockSfnServer.stubFor(post(urlEqualTo(sfnPublishSuccessPath))
    .willReturn(ok("Ok response body")))

  val s3Api: S3Mock = S3Mock(port = 8003, dir = "/tmp/s3")

  override def beforeAll(): Unit = {
    wiremockGraphqlServer.start()
    wiremockAuthServer.start()
    wiremockSfnServer.start()
    new File(scratchDirectory).mkdirs()
  }

  override def beforeEach(): Unit = {
    s3Api.start
    authOk
    wiremockGraphqlServer.resetAll()
    wiremockSfnServer.resetAll()
    graphqlUpdateExportLocation
    graphqlGetOriginalPath
    sfnPublishSuccess
    createBucket("test-clean-bucket")
    createBucket("test-output-bucket")
  }

  override def afterAll(): Unit = {
    wiremockGraphqlServer.stop()
    wiremockAuthServer.stop()
    wiremockSfnServer.stop()
  }

  override def afterEach(): Unit = {
    s3Api.stop
    deleteBucket("test-clean-bucket")
    deleteBucket("test-output-bucket")
    wiremockAuthServer.resetAll()
    wiremockGraphqlServer.resetAll()
    wiremockSfnServer.resetAll()
    Seq("sh", "-c", s"rm -r $scratchDirectory/*").!
  }
}

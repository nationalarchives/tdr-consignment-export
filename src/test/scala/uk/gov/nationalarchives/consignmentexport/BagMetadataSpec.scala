package uk.gov.nationalarchives.consignmentexport

import java.time.ZonedDateTime
import java.util.UUID

import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.{Series, TransferringBody}
import org.keycloak.representations.idm.UserRepresentation
import uk.gov.nationalarchives.consignmentexport.Config._
import uk.gov.nationalarchives.consignmentexport.Utils._

class BagMetadataSpec extends ExportSpec {

  private val fixedDateTime = ZonedDateTime.now()
  private val userId = UUID.randomUUID()
  private val series = Series(Some("series-code"))
  private val transferringBody = TransferringBody(Some("tb-code"))
  private val consignment = GetConsignment(
    userId, Some(fixedDateTime), Some(fixedDateTime), Some(fixedDateTime), Some(series), Some(transferringBody), List()
  )
  private val userRepresentation = new UserRepresentation()
  userRepresentation.setFirstName("FirstName")
  userRepresentation.setLastName("LastName")

  "the getBagMetadata method" should "return the correct bag metadata for the given consignment id" in {
    val consignmentId = UUID.randomUUID()
    val mockKeycloakClient = mock[KeycloakClient]

    doAnswer(() => userRepresentation).when(mockKeycloakClient).getUserDetails(any[String])
    val bagMetadata = BagMetadata(mockKeycloakClient).generateMetadata(consignmentId, consignment, fixedDateTime).unsafeRunSync()

    bagMetadata.get("Consignment-Series").get(0) should be("series-code")
    bagMetadata.get("Source-Organization").get(0) should be("tb-code")
    bagMetadata.get("Consignment-Start-Datetime").get(0) should be(fixedDateTime.toFormattedPrecisionString)
    bagMetadata.get("Consignment-Completed-Datetime").get(0) should be(fixedDateTime.toFormattedPrecisionString)
    bagMetadata.get("Contact-Name").get(0) should be("FirstName LastName")
    bagMetadata.get("Consignment-Export-Datetime").get(0) should be(fixedDateTime.toFormattedPrecisionString)
  }

  "the getBagMetadata method" should "throw an exception if a consignment metadata property is missing" in {
    val missingPropertyKey = "Consignment-Start-Datetime"
    val consignmentId = UUID.randomUUID()

    val incompleteConsignment = GetConsignment(
      userId, None, Some(fixedDateTime), Some(fixedDateTime), Some(series), Some(transferringBody), List()
    )
    val mockKeycloakClient = mock[KeycloakClient]

    doAnswer(() => userRepresentation).when(mockKeycloakClient).getUserDetails(any[String])

    val exception = intercept[RuntimeException] {
      BagMetadata(mockKeycloakClient).generateMetadata(consignmentId, incompleteConsignment, fixedDateTime).unsafeRunSync()
    }
    exception.getMessage should equal(s"Missing consignment metadata property $missingPropertyKey for consignment $consignmentId")
  }

  "the getBagMetadata method" should "throw an exception if no consignment meta data is returned" in {
    val mockKeycloakClient = mock[KeycloakClient]
    val mockGraphQlApi = mock[GraphQlApi]
    val consignmentId = UUID.fromString("50df01e6-2e5e-4269-97e7-531a755b417d")
    val config = Configuration(S3("", "", ""), Api(""), Auth("authUrl", "clientId", "clientSecret", "realm"), EFS(""))

    doAnswer(() => IO.pure(None)).when(mockGraphQlApi).getConsignmentMetadata(config, consignmentId)

    val exception = intercept[RuntimeException] {
      BagMetadata(mockGraphQlApi, mockKeycloakClient).getBagMetadata(consignmentId, config, fixedDateTime).unsafeRunSync()
    }
    exception.getMessage should equal(s"No consignment metadata found for consignment $consignmentId")
  }

  "the getBagMetadata method" should "throw an exception if incomplete user details are found" in {
    val mockKeycloakClient = mock[KeycloakClient]
    val consignmentId = UUID.randomUUID()
    val incompleteUserRepresentation = new UserRepresentation()
    incompleteUserRepresentation.setLastName("LastName")

    doAnswer(() => incompleteUserRepresentation).when(mockKeycloakClient).getUserDetails(userId.toString)

    val exception = intercept[RuntimeException] {
      BagMetadata(mockKeycloakClient).generateMetadata(consignmentId, consignment, fixedDateTime).unsafeRunSync()
    }
    exception.getMessage should equal(s"Incomplete details for user $userId")
  }
}

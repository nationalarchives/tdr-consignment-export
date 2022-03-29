package uk.gov.nationalarchives.consignmentexport

import java.time.ZonedDateTime
import java.util.UUID

import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.{Series, TransferringBody}
import org.keycloak.representations.idm.UserRepresentation
import uk.gov.nationalarchives.consignmentexport.BuildInfo.version
import uk.gov.nationalarchives.consignmentexport.Utils._
import cats.effect.unsafe.implicits.global

class BagMetadataSpec extends ExportSpec {

  private val fixedDateTime = ZonedDateTime.now()
  private val userId = UUID.randomUUID()
  private val series = Series("series-code")
  private val transferringBody = TransferringBody("tb-name")
  private val consignmentRef = "consignmentReference-1234"
  private val standardConsignmentType = "standard"
  private val JudgmentConsignmentType = "judgment"
  private val consignment = GetConsignment(
    userId, Some(fixedDateTime), Some(fixedDateTime), Some(fixedDateTime), consignmentRef, Some(standardConsignmentType), Some(series), Some(transferringBody), List()
  )
  private val userRepresentation = new UserRepresentation()
  userRepresentation.setId(userId.toString)
  userRepresentation.setFirstName("FirstName")
  userRepresentation.setLastName("LastName")
  userRepresentation.setEmail("firstName.lastName@something.com")

  "the getBagMetadata method" should "return the correct bag metadata for the given consignment id" in {
    val consignmentId = UUID.randomUUID()
    val mockKeycloakClient = mock[KeycloakClient]

    doAnswer(() => userRepresentation).when(mockKeycloakClient).getUserRepresentation(any[String])
    val bagMetadata = BagMetadata(mockKeycloakClient).generateMetadata(consignmentId, consignment, fixedDateTime).unsafeRunSync()

    bagMetadata.get("Consignment-Series").get(0) should be("series-code")
    bagMetadata.get("Source-Organization").get(0) should be("tb-name")
    bagMetadata.get("Consignment-Type").get(0) should be ("standard")
    bagMetadata.get("Internal-Sender-Identifier").get(0) should be("consignmentReference-1234")
    bagMetadata.get("Consignment-Start-Datetime").get(0) should be(fixedDateTime.toFormattedPrecisionString)
    bagMetadata.get("Consignment-Completed-Datetime").get(0) should be(fixedDateTime.toFormattedPrecisionString)
    bagMetadata.get("Contact-Name").get(0) should be("FirstName LastName")
    bagMetadata.get("Contact-Email").get(0) should be("firstName.lastName@something.com")
    bagMetadata.get("Consignment-Export-Datetime").get(0) should be(fixedDateTime.toFormattedPrecisionString)
    bagMetadata.get("Bag-Creator").get(0) should be(s"TDRExportv$version")
  }

  "the getBagMetadata method" should "throw an exception if a consignment metadata property is missing" in {
    val consignmentId = UUID.randomUUID()
    val missingPropertyKey = "Consignment-Start-Datetime"
    val incompleteConsignment = GetConsignment(
      userId, None, Some(fixedDateTime), Some(fixedDateTime), consignmentRef, Some(standardConsignmentType), Some(series), Some(transferringBody), List()
    )
    val mockKeycloakClient = mock[KeycloakClient]

    doAnswer(() => userRepresentation).when(mockKeycloakClient).getUserRepresentation(any[String])

    val exception = intercept[RuntimeException] {
      BagMetadata(mockKeycloakClient).generateMetadata(consignmentId, incompleteConsignment, fixedDateTime).unsafeRunSync()
    }
    exception.getMessage should equal(s"Missing consignment metadata property $missingPropertyKey for consignment $consignmentId")
  }

  "the getBagMetadata method" should "throw an exception if incomplete user details are found" in {
    val mockKeycloakClient = mock[KeycloakClient]
    val consignmentId = UUID.randomUUID()
    val incompleteUserRepresentation = new UserRepresentation()
    incompleteUserRepresentation.setId(userId.toString)
    incompleteUserRepresentation.setLastName("LastName")

    doAnswer(() => incompleteUserRepresentation).when(mockKeycloakClient).getUserRepresentation(userId.toString)

    val exception = intercept[RuntimeException] {
      BagMetadata(mockKeycloakClient).generateMetadata(consignmentId, consignment, fixedDateTime).unsafeRunSync()
    }
    exception.getMessage should equal(s"Incomplete details for user $userId")
  }

  "the getBagMetadata method" should "return an empty series id for a 'judgment' consignment type" in {
    val consignmentId = UUID.randomUUID()
    val judgmentTypeConsignment = GetConsignment(
      userId, Some(fixedDateTime), Some(fixedDateTime), Some(fixedDateTime), consignmentRef, Some(JudgmentConsignmentType), None, Some(transferringBody), List()
    )
    val mockKeycloakClient = mock[KeycloakClient]

    doAnswer(() => userRepresentation).when(mockKeycloakClient).getUserRepresentation(any[String])
    val bagMetadata = BagMetadata(mockKeycloakClient).generateMetadata(consignmentId, judgmentTypeConsignment, fixedDateTime).unsafeRunSync()

    bagMetadata.get("Consignment-Series").get(0) should be("")
  }
}

package uk.gov.nationalarchives.consignmentexport

import uk.gov.nationalarchives.consignmentexport.Config._

class KeycloakClientSpec extends ExternalServiceSpec {
  private val config = Configuration(
    S3("", "", "", "", 0, 0),
    Api(""),
    Auth("http://localhost:9002/auth", "tdr-backend-checks", "client-secret", "tdr"),
    EFS(""),
    StepFunction(""),
    ConsignmentTypeOverride(List(), List())
  )

  "the getUserDetails method" should "return the correct user details" in {
    keycloakGetUser
    val keycloakClient = new KeycloakClient(keycloakCreateAdminClient, config)

    val userDetails = keycloakClient.getUserRepresentation(keycloakUserId)
    userDetails.getFirstName should be("FirstName")
    userDetails.getLastName should be("LastName")
  }

  "the getUserDetails method" should "throw a run time exception if no user representation found" in {
    val nonExistentUserId = "nonExistentUserId"
    val keycloakClient = new KeycloakClient(keycloakCreateAdminClient, config)

    val exception = intercept[RuntimeException] {
      keycloakClient.getUserRepresentation(nonExistentUserId)
    }
    exception.getMessage should equal(s"No valid user found $nonExistentUserId: HTTP 404 Not Found")
  }
}

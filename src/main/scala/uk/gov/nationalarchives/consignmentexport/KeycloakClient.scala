package uk.gov.nationalarchives.consignmentexport

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.resource.{RealmResource, UsersResource}
import org.keycloak.admin.client.{Keycloak, KeycloakBuilder}
import org.keycloak.representations.idm.UserRepresentation
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend}
import uk.gov.nationalarchives.consignmentexport.Config.Configuration

import scala.language.postfixOps

class KeycloakClient(keycloakAdminClient: Keycloak, config: Configuration) {
  private def realmResource(client: Keycloak): RealmResource = client.realm(config.auth.realm)
  private def usersResource(realm: RealmResource): UsersResource = realm.users()

  def getUserRepresentation(userId: String): UserRepresentation = {
    val realm = realmResource(keycloakAdminClient)
    val users = usersResource(realm)

    try {
      val userResource = users.get(userId)
      userResource.toRepresentation
    } catch {
      case e: Exception => throw new RuntimeException(s"No valid user found $userId: ${e.getMessage}")
    }
  }
}

object KeycloakClient {
  implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  def apply(config: Configuration): KeycloakClient = {
    val keycloakAdminClient = KeycloakBuilder.builder()
      .serverUrl(config.auth.url)
      .realm(config.auth.realm)
      .clientId(config.auth.clientId)
      .clientSecret(config.auth.clientSecret)
      .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
      .build()
    new KeycloakClient(keycloakAdminClient, config)
  }
}

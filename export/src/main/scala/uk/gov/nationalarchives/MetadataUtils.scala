package uk.gov.nationalarchives

import cats.effect.IO
import doobie.{Get, Transactor}
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.rds.RdsUtilities
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest
import uk.gov.nationalarchives.Main.Config
import uk.gov.nationalarchives.MetadataUtils._

import java.util.UUID

class MetadataUtils(config: Config) {
  private def dbPassword: String = {
    if (config.db.useIamAuth) {
      val rdsClient = RdsUtilities.builder().region(Region.EU_WEST_2).build()
      val port = config.db.port
      val request = GenerateAuthenticationTokenRequest
        .builder()
        .credentialsProvider(DefaultCredentialsProvider.builder().build())
        .hostname(config.db.host)
        .port(port)
        .username(config.db.user)
        .region(Region.EU_WEST_2)
        .build()
      rdsClient.generateAuthenticationToken(request)
    } else {
      config.db.password
    }
  }

  private def transactor: Aux[IO, Unit] = {
    val suffix = if(config.db.useIamAuth) {
      val certificatePath = getClass.getResource("/rds-eu-west-2-bundle.pem").getPath
      s"?ssl=true&sslrootcert=$certificatePath&sslmode=verify-full"
    } else {
      ""
    }
    val jdbcUrl = s"jdbc:postgresql://${config.db.host}:5432/consignmentapi$suffix"
    Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver", url = jdbcUrl, user = config.db.user, password = dbPassword, logHandler = None
    )
  }

  implicit val consignmentTypeGet: Get[ConsignmentType] = Get[String].map {
    case "judgment" => Judgment
    case "standard" => Standard
  }

  def getConsignmentType(consignmentId: UUID): IO[ConsignmentType] =
    sql""" select "ConsignmentType" from "Consignment" where "ConsignmentId" = ${consignmentId.toString} """
      .query[ConsignmentType].unique.transact(transactor)


  def getMetadata(consignmentId: UUID): IO[List[Metadata]] =
    sql"""select fm."FileId", "PropertyName", "Value" from "File" f JOIN "FileMetadata" fm on fm."FileId" = f."FileId" where "ConsignmentId" = ${consignmentId.toString} """
      .query[Metadata].to[List].transact(transactor)



}
object MetadataUtils {
  implicit val get: Get[UUID] = Get[String].map(UUID.fromString)
  case class Metadata(fileId: UUID, PropertyName: String, Value: String)
  sealed trait ConsignmentType
  case object Judgment extends ConsignmentType
  case object Standard extends ConsignmentType
  def apply(config: Config): IO[MetadataUtils] = IO(new MetadataUtils(config))
}

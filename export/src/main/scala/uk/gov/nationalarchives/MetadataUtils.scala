package uk.gov.nationalarchives

import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux
import doobie.{Get, Transactor}
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

  val transactor: Aux[IO, Unit] = {
    val suffix = if (config.db.useIamAuth) {
      s"?ssl=true&sslrootcert=/home/consignment-export/eu-west-2-bundle.pem&sslmode=verify-full"
    } else {
      ""
    }
    val jdbcUrl = s"jdbc:postgresql://${config.db.host}:${config.db.port}/consignmentapi$suffix"
    Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = jdbcUrl,
      user = config.db.user,
      password = dbPassword,
      logHandler = None
    )
  }

  implicit val consignmentTypeGet: Get[ConsignmentType] = Get[String].map {
    case "judgment" => Judgment
    case "standard" => Standard
  }

  def getConsignmentType(consignmentId: UUID): IO[ConsignmentType] =
    sql""" select "ConsignmentType" from "Consignment"
         WHERE "ConsignmentId" = CAST(${consignmentId.toString} AS UUID)"""
      .query[ConsignmentType]
      .unique
      .transact(transactor)

  def getConsignmentMetadata(consignmentId: UUID): IO[List[Metadata]] = {
    (for {
      consignmentMetadata <-
        sql""" SELECT "ConsignmentId", "PropertyName", "Value"
           FROM "ConsignmentMetadata" WHERE "ConsignmentId" = CAST(${consignmentId.toString} AS UUID) """
          .query[Metadata]
          .to[List]
          .transact(transactor)
      bodyRefAndSeries <-
        sql""" select b."Name",  "ConsignmentReference", s."Name"
           FROM "Consignment" c
           JOIN "Body" b on b."BodyId" = c."BodyId"
           JOIN "Series" s on b."BodyId" = s."BodyId"
           WHERE  "ConsignmentId" = CAST(${consignmentId.toString} AS UUID) """
          .query[(String, String, String)]
          .unique
          .transact(transactor)
    } yield {
      consignmentMetadata ++ List(
        Metadata(consignmentId, "TransferringBody", bodyRefAndSeries._1),
        Metadata(consignmentId, "ConsignmentReference", bodyRefAndSeries._2),
        Metadata(consignmentId, "Series", bodyRefAndSeries._3)
      )
    }).handleErrorWith(_ => IO.raiseError(new Exception(s"Cannot find a consignment for id $consignmentId")))
  }

  private def getAvMetadata(consignmentId: UUID): IO[List[Metadata]] = for {
    avRows <- sql"""select av."FileId", "Software", "SoftwareVersion", "Result", av."Datetime"
         FROM "AVMetadata" av JOIN "File" f on f."FileId" = av."FileId"
         where "ConsignmentId" = CAST(${consignmentId.toString} AS UUID) """
      .query[(String, String, String, String, String)]
      .to[List]
      .transact(transactor)
  } yield avRows.flatMap { avRow =>
    val (fileIdString, software, softwareVersion, result, datetime) = avRow
    val fileId = UUID.fromString(fileIdString)
    List(
      Metadata(fileId, "antivirusSoftware", software),
      Metadata(fileId, "antivirusSoftwareVersion", softwareVersion),
      Metadata(fileId, "antivirusResult", result),
      Metadata(fileId, "antivirusDatetime", datetime)
    )
  }

  def getFFIDMetadata(consignmentId: UUID): IO[Map[UUID, List[FFID]]] = for {
    ffidMetadataRows <- sql"""SELECT fm."FileId", "Extension", "IdentificationBasis", "PUID", "ExtensionMismatch", "FormatName"
                         FROM "FFIDMetadataMatches" fmm
                                  join "FFIDMetadata" fm on fm."FFIDMetadataId" = fmm."FFIDMetadataId"
                                  join "File" f on f."FileId" = fm."FileId"
                                  WHERE f."ConsignmentId" = CAST(${consignmentId.toString} AS UUID)"""
      .query[(String, String, String, String, Boolean, String)]
      .to[List]
      .transact(transactor)
  } yield {
    ffidMetadataRows.groupBy(_._1).map { groupedRow =>
      val (fileId, ffidRows) = groupedRow
      val metadataRows = ffidRows.map { ffidMetadataRow =>
        val (_, extension, identificationBasis, puid, extensionMismatch, formatName) = ffidMetadataRow
        FFID(extension, identificationBasis, puid, extensionMismatch, formatName)
      }
      UUID.fromString(fileId) -> metadataRows
    }
  }

  def getFileMetadata(consignmentId: UUID): IO[List[Metadata]] =
    for {
      fileMetadata <- sql"""select fm."FileId", "PropertyName", "Value"
         FROM "File" f
         JOIN "FileMetadata" fm on fm."FileId" = f."FileId"
         WHERE "ConsignmentId" = CAST(${consignmentId.toString} AS UUID)"""
        .query[Metadata]
        .to[List]
        .transact(transactor)
      avMetadata <- getAvMetadata(consignmentId)
    } yield processRedactions(fileMetadata) ++ avMetadata

  private def processRedactions(fileMetadata: List[Metadata]): List[Metadata] =
    fileMetadata ++ fileMetadata.find(_.PropertyName == "OriginalFilepath").flatMap { originalFilePathRow =>
      val originalId = fileMetadata
        .find(fm => fm.PropertyName == "ClientSideOriginalFilepath" && fm.Value == originalFilePathRow.Value)
        .map(_.id)
      fileMetadata
        .find(fm => originalId.contains(fm.id) && fm.PropertyName == "FileReference")
        .map(fm => Metadata(originalFilePathRow.id, "OriginalFileReference", fm.Value))
    }
}
object MetadataUtils {
  implicit val get: Get[UUID] = Get[String].map(UUID.fromString)
  case class Metadata(id: UUID, PropertyName: String, Value: String)
  sealed trait ConsignmentType
  case object Judgment extends ConsignmentType
  case object Standard extends ConsignmentType
  case class FFID(extension: String, identificationBasis: String, puid: String, extensionMismatch: Boolean, formatName: String)

  def apply(config: Config): IO[MetadataUtils] = IO(new MetadataUtils(config))
}

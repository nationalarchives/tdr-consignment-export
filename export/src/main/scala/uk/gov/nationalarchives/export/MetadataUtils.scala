package uk.gov.nationalarchives.`export`

import cats.effect.IO
import doobie.implicits._
import doobie.util.transactor.Transactor.Aux
import doobie.{Get, Transactor}
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.rds.RdsUtilities
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest
import Main.Config
import MetadataUtils._

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
    sql""" SELECT "ConsignmentType" FROM "Consignment"
         WHERE "ConsignmentId" = CAST(${consignmentId.toString} AS UUID)"""
      .query[ConsignmentType]
      .unique
      .transact(transactor)
      .handleErrorWith(_ => IO.raiseError(new Exception(s"Cannot find a consignment for id $consignmentId")))

  def getConsignmentMetadata(consignmentId: UUID): IO[List[Metadata]] = {
    (for {
      consignmentMetadata <-
        sql""" SELECT "ConsignmentId", "PropertyName", "Value"
           FROM "ConsignmentMetadata" WHERE "ConsignmentId" = CAST(${consignmentId.toString} AS UUID) """
          .query[Metadata]
          .to[List]
          .transact(transactor)
      bodyRefAndSeries <-
        sql""" SELECT b."Name",  "ConsignmentReference", COALESCE(s."Name", '')
           FROM "Consignment" c
           JOIN "Body" b ON b."BodyId" = c."BodyId"
           LEFT JOIN "Series" s ON c."SeriesId" = s."SeriesId"
           WHERE  "ConsignmentId" = CAST(${consignmentId.toString} AS UUID)
           """
          .query[(String, String, String)]
          .unique
          .transact(transactor)
      transferCompleteDate <-
        sql""" SELECT TO_CHAR("TransferInitiatedDatetime",'YYYY-MM-DD HH:MI:SS') FROM "Consignment"
            WHERE "ConsignmentId" = CAST(${consignmentId.toString} AS UUID)
           """
          .query[Option[String]] //This shouldn't ever be empty but if it is it will crash the export so better safe than sorry
          .unique
          .transact(transactor)
    } yield {
      consignmentMetadata ++ List(
        Metadata(consignmentId, "TransferringBody", bodyRefAndSeries._1),
        Metadata(consignmentId, "ConsignmentReference", bodyRefAndSeries._2),
        Metadata(consignmentId, "Series", bodyRefAndSeries._3),
        Metadata(consignmentId, "TransferInitiatedDatetime", transferCompleteDate.getOrElse(""))
      )
    })
  }

  private def getAvMetadata(consignmentId: UUID): IO[List[Metadata]] = for {
    avRows <- sql"""SELECT av."FileId", "Software", "SoftwareVersion", "Result", av."Datetime"
         FROM "AVMetadata" av JOIN "File" f ON f."FileId" = av."FileId"
         WHERE "ConsignmentId" = CAST(${consignmentId.toString} AS UUID) """
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
                                  JOIN "FFIDMetadata" fm ON fm."FFIDMetadataId" = fmm."FFIDMetadataId"
                                  JOIN "File" f ON f."FileId" = fm."FileId"
                                  WHERE f."ConsignmentId" = CAST(${consignmentId.toString} AS UUID)"""
      .query[(String, Option[String], String, Option[String], Boolean, Option[String])]
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
      fileMetadata <- sql"""SELECT fm."FileId", "PropertyName", "Value"
         FROM "File" f
         JOIN "FileMetadata" fm ON fm."FileId" = f."FileId"
         WHERE "ConsignmentId" = CAST(${consignmentId.toString} AS UUID)"""
        .query[Metadata]
        .to[List]
        .transact(transactor)
      avMetadata <- getAvMetadata(consignmentId)
    } yield processRedactions(fileMetadata) ++ avMetadata

  private def processRedactions(fileMetadata: List[Metadata]): List[Metadata] =
    fileMetadata ++ fileMetadata.find(_.propertyName == "OriginalFilepath").flatMap { originalFilePathRow =>
      val originalId = fileMetadata
        .find(fm => fm.propertyName == "ClientSideOriginalFilepath" && fm.value == originalFilePathRow.value)
        .map(_.id)
      fileMetadata
        .find(fm => originalId.contains(fm.id) && fm.propertyName == "FileReference")
        .map(fm => Metadata(originalFilePathRow.id, "OriginalFileReference", fm.value))
    }
}

object MetadataUtils {
  implicit val get: Get[UUID] = Get[String].map(UUID.fromString)
  case class Metadata(id: UUID, propertyName: String, value: String)
  sealed trait ConsignmentType
  case object Judgment extends ConsignmentType
  case object Standard extends ConsignmentType
  case class FFID(extension: Option[String], identificationBasis: String, puid: Option[String], extensionMismatch: Boolean, formatName: Option[String])

  def apply(config: Config): IO[MetadataUtils] = IO(new MetadataUtils(config))
}

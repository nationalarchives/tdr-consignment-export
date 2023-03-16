package uk.gov.nationalarchives.consignmentexport

import java.time.ZonedDateTime
import java.util.UUID
import cats.effect.IO
import gov.loc.repository.bagit.domain.Metadata
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import org.keycloak.representations.idm.UserRepresentation
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.consignmentexport.BagMetadata.{BagCreator, _}
import uk.gov.nationalarchives.consignmentexport.BuildInfo.version
import uk.gov.nationalarchives.consignmentexport.Utils._

class BagMetadata(keycloakClient: KeycloakClient)(implicit val logger: SelfAwareStructuredLogger[IO]) {

  implicit class UserRepresentationUtils(value: UserRepresentation) {
    private def isValuePresent(s: String): Boolean = s != null && s.trim.nonEmpty

    private def isUserRepresentationComplete: Boolean =
      isValuePresent(value.getFirstName) && isValuePresent(value.getLastName) && isValuePresent(value.getEmail)

    def toUserDetails: UserDetails = if (isUserRepresentationComplete) {
      UserDetails(s"${value.getFirstName} ${value.getLastName}", value.getEmail)
    } else { throw new RuntimeException(s"Incomplete details for user ${value.getId}") }
  }

  private def getConsignmentDetails(consignment: GetConsignment, exportDatetime: ZonedDateTime): Map[String, Option[String]] = {
    val consignmentType: Option[String] = consignment.consignmentType
    val seriesCode:Option[String]  = if(consignmentType.get.equals("judgment")){
      Some("")
    } else {
      consignment.series.map(_.code)
    }
    val bodyName: Option[String] = consignment.transferringBody.map(_.name)
    val startDatetime: Option[String] = consignment.createdDatetime.map(_.toFormattedPrecisionString)
    val completedDatetime: Option[String] = consignment.transferInitiatedDatetime.map(_.toFormattedPrecisionString)
    val includeTopLevelFolder: Option[String] = consignment.includeTopLevelFolder.map(_.toString)
    val userDetails: UserDetails = keycloakClient.getUserRepresentation(consignment.userid.toString).toUserDetails

    Map(
      InternalSenderIdentifierKey -> Some(consignment.consignmentReference),
      ConsignmentSeriesKey -> seriesCode,
      SourceOrganisationKey -> bodyName,
      ConsignmentTypeKey -> consignmentType,
      ConsignmentIncludeTopLevelFolder -> includeTopLevelFolder,
      ConsignmentStartDatetimeKey -> startDatetime,
      ConsignmentCompletedDatetimeKey -> completedDatetime,
      ConsignmentExportDatetimeKey -> Some(exportDatetime.toFormattedPrecisionString),
      ContactNameKey -> Some(userDetails.contactName),
      ContactEmailKey -> Some(userDetails.contactEmail),
      BagCreator -> Some(s"TDRExportv$version")
    )
  }

  def generateMetadata(consignmentId: UUID, consignment: GetConsignment, exportDatetime: ZonedDateTime): IO[Metadata] = {
    val details: Map[String, Option[String]] = getConsignmentDetails(consignment, exportDatetime)
    val metadata = new Metadata

    details.map(e => {
      e._2 match {
        case Some(_) => metadata.add(e._1, e._2.getOrElse(""))
        case None => throw new RuntimeException(s"Missing consignment metadata property ${e._1} for consignment $consignmentId")
      }
    })
    IO(metadata)
  }
}

object BagMetadata {
  val SourceOrganisationKey = "Source-Organization"
  val ConsignmentSeriesKey = "Consignment-Series"
  val ConsignmentStartDatetimeKey = "Consignment-Start-Datetime"
  val ConsignmentCompletedDatetimeKey = "Consignment-Completed-Datetime"
  val ContactNameKey = "Contact-Name"
  val ContactEmailKey = "Contact-Email"
  val ConsignmentExportDatetimeKey = "Consignment-Export-Datetime"
  val BagCreator = "Bag-Creator"
  val InternalSenderIdentifierKey = "Internal-Sender-Identifier"
  val ConsignmentTypeKey = "Consignment-Type"
  val ConsignmentIncludeTopLevelFolder = "Consignment-Include-Top-Level-Folder"

  case class UserDetails(contactName: String, contactEmail: String)

  def apply(keycloakClient: KeycloakClient)(implicit logger: SelfAwareStructuredLogger[IO]): BagMetadata = new BagMetadata(keycloakClient)(logger)
}



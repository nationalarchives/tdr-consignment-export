package uk.gov.nationalarchives.consignmentexport

import java.time.ZonedDateTime
import java.util.UUID

import cats.effect.IO
import gov.loc.repository.bagit.domain.Metadata
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import org.keycloak.representations.idm.UserRepresentation
import uk.gov.nationalarchives.consignmentexport.BagMetadata.{BagCreator, _}
import uk.gov.nationalarchives.consignmentexport.BuildInfo.version
import uk.gov.nationalarchives.consignmentexport.Utils._

class BagMetadata(keycloakClient: KeycloakClient)(implicit val logger: SelfAwareStructuredLogger[IO]) {

  implicit class UserRepresentationUtils(value: UserRepresentation) {
    private def isStringNullOrEmpty(s: String): Boolean = s == null || s.trim.isEmpty

    def isUserRepresentationComplete: Boolean = {
      !isStringNullOrEmpty(value.getFirstName) && !isStringNullOrEmpty(value.getLastName)
    }
  }

  private def getConsignmentDetails(consignment: GetConsignment, exportDatetime: ZonedDateTime): Map[String, Option[String]] = {
    val seriesCode: Option[String] = consignment.series.map(_.code)
    val bodyName: Option[String] = consignment.transferringBody.map(_.name)
    val consignmentType: Option[String] = consignment.consignmentType
    val startDatetime: Option[String] = consignment.createdDatetime.map(_.toFormattedPrecisionString)
    val completedDatetime: Option[String] = consignment.transferInitiatedDatetime.map(_.toFormattedPrecisionString)
    val contactName: String = getContactName(consignment.userid)

    Map(
      InternalSenderIdentifierKey -> Some(consignment.consignmentReference),
      ConsignmentSeriesKey -> seriesCode,
      SourceOrganisationKey -> bodyName,
      ConsignmentTypeKey -> consignmentType,
      ConsignmentStartDatetimeKey -> startDatetime,
      ConsignmentCompletedDatetimeKey -> completedDatetime,
      ConsignmentExportDatetimeKey -> Some(exportDatetime.toFormattedPrecisionString),
      ContactNameKey -> Some(contactName),
      BagCreator -> Some(s"TDRExportv$version")
    )
  }

  def generateMetadata(consignmentId: UUID, consignment: GetConsignment, exportDatetime: ZonedDateTime): IO[Metadata] = {
    val details: Map[String, Option[String]] = getConsignmentDetails(consignment, exportDatetime)
    val metadata = new Metadata

    details.map(e => {
      e._2 match {
        case Some(_) => metadata.add(e._1, e._2.get)
        case None => throw new RuntimeException(s"Missing consignment metadata property ${e._1} for consignment $consignmentId")
      }
    })
    IO(metadata)
  }

  private def getContactName(userId: UUID): String = {
    val userDetails = getUserDetails(userId.toString)
    s"${userDetails.getFirstName} ${userDetails.getLastName}"
  }

  private def getUserDetails(userId: String): UserRepresentation = {
    val userDetails = keycloakClient.getUserDetails(userId)
    userDetails.isUserRepresentationComplete match {
      case true => userDetails
      case _ => throw new RuntimeException(s"Incomplete details for user $userId")
    }
  }
}

object BagMetadata {
  val SourceOrganisationKey = "Source-Organization"
  val ConsignmentSeriesKey = "Consignment-Series"
  val ConsignmentStartDatetimeKey = "Consignment-Start-Datetime"
  val ConsignmentCompletedDatetimeKey = "Consignment-Completed-Datetime"
  val ContactNameKey = "Contact-Name"
  val ConsignmentExportDatetimeKey = "Consignment-Export-Datetime"
  val BagCreator = "Bag-Creator"
  val InternalSenderIdentifierKey = "Internal-Sender-Identifier"
  val ConsignmentTypeKey = "Consignment-Type"

  def apply(keycloakClient: KeycloakClient)(implicit logger: SelfAwareStructuredLogger[IO]): BagMetadata = new BagMetadata(keycloakClient)(logger)
}

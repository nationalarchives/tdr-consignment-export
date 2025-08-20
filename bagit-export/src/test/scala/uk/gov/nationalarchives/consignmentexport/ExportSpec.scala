package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.FileMetadata
import org.mockito.scalatest.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

abstract class ExportSpec extends AnyFlatSpec with MockitoSugar with Matchers with EitherValues {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def createFile(originalFilePath: String, fileMetadata: List[FileMetadata], fileType: String, fileName: String, fileRef: String, parentRef: Option[String] = None): Files = Files(
    UUID.randomUUID(),
    fileType.some,
    fileName.some,
    fileRef.some,
    parentRef,
    originalFilePath.some,
    fileMetadata,
    None,
    None
  )

  def createMetadata(
                      lastModified: LocalDateTime,
                      originalPath: String = "originalPath",
                      checkSum: String = "clientSideChecksumValue"): List[FileMetadata] = {
    List(
      FileMetadata("FileReference", "fileReference"),
      FileMetadata("Filename", "fileName"),
      FileMetadata("FileType", "fileType"),
      FileMetadata("ClientSideFileSize", "1"),
      FileMetadata("ClientSideFileLastModifiedDate", lastModified.format(DateTimeFormatter.ISO_DATE_TIME)),
      FileMetadata("ClientSideOriginalFilepath", originalPath),
      FileMetadata("FoiExemptionCode", "foiExemption;foiExemption2"),
      FileMetadata("HeldBy", "heldBy"),
      FileMetadata("Language", "language"),
      FileMetadata("LegalStatus", "legalStatus"),
      FileMetadata("RightsCopyright", "rightsCopyright"),
      FileMetadata("SHA256ClientSideChecksum", checkSum),
      FileMetadata("OriginalFilepath", "nonRedactedFilepath"),
      FileMetadata("UUID", "uuid"),
      FileMetadata("former_reference_department", "formerReferenceDepartment"),
      FileMetadata("ParentReference", "parentReference"),
      FileMetadata("file_name_translation", "fileNameTranslation"),
      FileMetadata("end_date", lastModified.format(DateTimeFormatter.ISO_DATE_TIME)),
      FileMetadata("DescriptionAlternate", "descriptionAlternate"),
      FileMetadata("DescriptionClosed", "descriptionClosed"),
      FileMetadata("description", "description"),
      FileMetadata("TitleAlternate", "titleAlternate"),
      FileMetadata("TitleClosed", "titleClosed"),
      FileMetadata("FoiExemptionAsserted", lastModified.format(DateTimeFormatter.ISO_DATE_TIME)),
      FileMetadata("ClosurePeriod", "30"),
      FileMetadata("ClosureStartDate", lastModified.format(DateTimeFormatter.ISO_DATE_TIME)),
      FileMetadata("ClosureType", "closureType")
    )
  }
}

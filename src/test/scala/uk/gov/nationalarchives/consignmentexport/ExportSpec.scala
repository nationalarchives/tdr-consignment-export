package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType
import graphql.codegen.types.DataType.{DateTime, Text}
import graphql.codegen.types.PropertyType.Defined
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.mockito.scalatest.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

abstract class ExportSpec extends AnyFlatSpec with MockitoSugar with Matchers with EitherValues {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def createFile(originalFilePath: String, fileMetadata: List[FileMetadata], fileType: String, fileName: String): Files = Files(
    UUID.randomUUID(),
    fileType.some,
    fileName.some,
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
      FileMetadata("Filename", "File Name"),
      FileMetadata("FileType", "File"),
      FileMetadata("ClientSideFileSize", "1"),
      FileMetadata("ClientSideLastModifiedDate", lastModified.format(DateTimeFormatter.ISO_DATE_TIME)),
      FileMetadata("ClientSideOriginalFilepath", originalPath),
      FileMetadata("FoiExemptionCode", "foiExemption"),
      FileMetadata("FoiExemptionCode", "foiExemption2"),
      FileMetadata("HeldBy", "heldBy"),
      FileMetadata("Language", "language"),
      FileMetadata("LegalStatus", "legalStatus"),
      FileMetadata("RightsCopyright", "rightsCopyright"),
      FileMetadata("SHA256ClientSideChecksum", checkSum)
    )
  }
}
object ExportSpec {
  def createCustomMetadata(name: String, fullName: String, exportOrdinal: Int, dataType: DataType = Text, allowExport: Boolean = true): CustomMetadata = CustomMetadata(name, None, Some(fullName), Defined, Some("MandatoryClosure"), dataType, editable = true, multiValue = false,
    Some("Open"),
    1,
    Nil,
    Option(exportOrdinal),
    allowExport = allowExport
  )

  val customMetadata: List[CustomMetadata] = List(
    createCustomMetadata("ClientSideFileSize", "File Size", 4),
    createCustomMetadata("ClientSideLastModifiedDate", "Last Modified Date", 10, DateTime),
    createCustomMetadata("ClientSideOriginalFilepath", "File Path", 1),
    createCustomMetadata("Filename", "File Name", 2),
    createCustomMetadata("FileType", "File Type", 3),
    createCustomMetadata("FoiExemptionCode", "FOI Exemption Code", 9),
    createCustomMetadata("HeldBy", "Held By", 7),
    createCustomMetadata("Language", "Language", 8),
    createCustomMetadata("LegalStatus", "Legal Status", 6),
    createCustomMetadata("RightsCopyright", "Rights Copyright", 5),
    createCustomMetadata("SHA256ClientSideChecksum", "Checksum", 11)
  )
}

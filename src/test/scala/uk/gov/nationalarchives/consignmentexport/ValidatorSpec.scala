package uk.gov.nationalarchives.consignmentexport

import java.time.{LocalDateTime, ZonedDateTime}
import java.util.UUID
import cats.implicits._
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.FfidMetadata.Matches
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.{AntivirusMetadata, FfidMetadata, Metadata}
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.{Files, Series, TransferringBody}
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata, ValidatedFileMetadata}

class ValidatorSpec extends ExportSpec {

  private val completeFileMetadata = Files(
    UUID.randomUUID(),
    "File".some,
    Metadata(
      1L.some,
      LocalDateTime.now().some,
      "originalPath".some,
      "foiExemption".some,
      "heldBy".some,
      "language".some,
      "legalStatus".some,
      "rightsCopyright".some,
      "clientSideChecksum".some),
    Option.empty,
    Option.empty
  )

  private def consignment(consignmentId: UUID, metadata: List[Files] = List(completeFileMetadata)): GetConsignment = GetConsignment(
    consignmentId,
    ZonedDateTime.now().some,
    ZonedDateTime.now().some,
    ZonedDateTime.now().some,
    "consignmentRef",
    Some("standard"),
    Series("series-code").some,
    TransferringBody("tb-name").some,
    metadata
  )

  "validateConsignmentHasFiles" should "return an error if the consignment has no files" in {
    val consignmentId = UUID.randomUUID()
    val validator = Validator(consignmentId)
    val attempt: Either[Throwable, Files] = validator.validateConsignmentHasFiles(consignment(consignmentId, List()))
    attempt.left.value.getMessage should equal(s"Consignment API returned no files for consignment $consignmentId")
  }

  "validateConsignmentHasFiles" should "not return an error if the consignment has files" in {
    val consignmentId = UUID.randomUUID()
    val validator = Validator(consignmentId)
    val attempt: Either[Throwable, Files] = validator.validateConsignmentHasFiles(consignment(consignmentId))
    attempt.isRight should be(true)
  }

  "validateConsignmentResult" should "return an error if the consignment data is not defined" in {
    val consignmentId = UUID.randomUUID()
    val validator = Validator(consignmentId)
    val attempt = validator.validateConsignmentResult(none)
    attempt.left.value.getMessage should equal(s"No consignment metadata found for consignment $consignmentId")
  }

  "validateConsignmentResult" should "not return an error if the consignment data is defined" in {
    val consignmentId = UUID.randomUUID()
    val validator = Validator(consignmentId)
    val attempt = validator.validateConsignmentResult(consignment(consignmentId).some)
    attempt.isRight should be(true)
  }

  "extractFileMetadata" should "not return an error if all of the fields are set" in {
    val validator = Validator(UUID.randomUUID())
    val attempt: Either[Throwable, List[ValidatedFileMetadata]] = validator.extractFileMetadata(List(completeFileMetadata))
    attempt.right.value.length should equal(1)
  }

  "extractFileMetadata" should "return an error  if some of the fields are not set" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val fileIdTwo = UUID.randomUUID()
    val metadata = Files(
      fileId,
      "File".some,
      Metadata(
        1L.some,
        Option.empty,
        Option.empty,
        "foiExemption".some,
        "heldBy".some,
        "language".some,
        "legalStatus".some,
        "rightsCopyright".some,
        "clientSideChecksum".some
      ),
      Option.empty,
      Option.empty
    )
    val metadataTwo = Files(
      fileIdTwo,
      "File".some,
      Metadata(
        1L.some,
        LocalDateTime.parse("2021-02-03T10:33:30.414").some,
        "clientSideOriginalFilePath".some,
        Option.empty,
        Option.empty,
        Option.empty,
        "legalStatus".some,
        "rightsCopyright".some,
        "clientSideChecksum".some
      ),
      Option.empty,
      Option.empty
    )
    val file: Either[Throwable, List[ValidatedFileMetadata]] = validator.extractFileMetadata(List(metadata, metadataTwo))
    file.left.value.getMessage should equal(
      s"$fileId is missing the following properties: clientSideLastModifiedDate, clientSideOriginalFilePath\n$fileIdTwo is missing the following properties: foiExemptionCode, heldBy, language"
    )
  }
  "extractFileMetadata" should "return an error  if one file has some of the fields unset and one file has all fields set" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val metadata = Files(
      UUID.randomUUID(),
      "File".some,
      Metadata(
        1L.some,
        LocalDateTime.parse("2021-02-03T10:33:30.414").some,
        "clientSideOriginalFilePath".some,
        "foiExemption".some,
        "heldBy".some,
        "language".some,
        "legalStatus".some,
        "rightsCopyright".some,
        "clientSideChecksum".some
      ),
      Option.empty,
      Option.empty
    )
    val metadataTwo = Files(
      fileId,
      "File".some,
      Metadata(
        1L.some,
        LocalDateTime.parse("2021-02-03T10:33:30.414").some,
        "clientSideOriginalFilePath".some,
        "foiExemption".some,
        "heldBy".some,
        Option.empty,
        Option.empty,
        "rightsCopyright".some,
        "clientSideChecksum".some
      ),
      Option.empty,
      Option.empty
    )
    val file: Either[Throwable, List[ValidatedFileMetadata]] = validator.extractFileMetadata(List(metadata, metadataTwo))
    file.left.value.getMessage should equal(s"$fileId is missing the following properties: language, legalStatus")
  }

  "extractFFIDMetadata" should "return an error if the ffid metadata is missing" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val files = List(Files(fileId,"File".some, Metadata(None, None, None, None, None, None, None, None, None), Option.empty, Option.empty))
    val result = validator.extractFFIDMetadata(files)
    result.left.value.getMessage should equal(s"FFID metadata is missing for file id $fileId")
  }

  "extractFFIDMetadata" should "return an error if the ffid metadata is missing for one file and provided for another" in {
    val validator = Validator(UUID.randomUUID())
    val fileIdOne = UUID.randomUUID()
    val fileIdTwo = UUID.randomUUID()
    val metadata = Metadata(None, None, None, None, None, None, None, None, None)
    val files = List(Files(fileIdOne,"File".some, metadata, Option.empty, Option.empty), Files(fileIdTwo,"File".some, metadata, FfidMetadata("", "", "", "", "", List()).some, Option.empty))
    val result = validator.extractFFIDMetadata(files)
    result.left.value.getMessage should equal(s"FFID metadata is missing for file id $fileIdOne")
  }

  "extractFFIDMetadata" should "return success if the ffid metadata is present" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val metadata = Metadata(None, None, "filePath".some, None, None, None, None, None, None)
    val ffidMetadata = FfidMetadata("software", "softwareVersion", "binaryVersion", "containerVersion", "method", List(Matches("ext".some, "id", "puid".some)))
    val files = List(Files(fileId,"File".some, metadata, ffidMetadata.some, Option.empty))
    val result = validator.extractFFIDMetadata(files)
    val expectedResult = ValidatedFFIDMetadata("filePath", "ext", "puid", "software", "softwareVersion", "binaryVersion", "containerVersion")
    result.right.value.head should equal(expectedResult)
  }

  "extractAntivirusMetadata" should "return success if the antivirus metadata is present" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val metadata = Metadata(None, None, "filePath".some, None, None, None, None, None, None)
    val antivirusMetadata = AntivirusMetadata("software", "softwareVersion")
    val files = List(Files(fileId,"File".some, metadata, Option.empty, antivirusMetadata.some))
    val result = validator.extractAntivirusMetadata(files)
    val expectedResult = ValidatedAntivirusMetadata("filePath", "software", "softwareVersion")
    result.right.value.head should equal(expectedResult)
  }

  "extractAntivirusMetadata" should "return an error if the antivirus metadata is missing" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val metadata = Metadata(None, None, "filePath".some, None, None, None, None, None, None)
    val files = List(Files(fileId,"File".some, metadata, Option.empty, Option.empty))
    val result = validator.extractAntivirusMetadata(files)
    result.left.value.getMessage should equal(s"Antivirus metadata is missing for file id $fileId")
  }
}

package uk.gov.nationalarchives.consignmentexport

import java.time.{LocalDateTime, ZonedDateTime}
import java.util.UUID
import cats.implicits._
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.FfidMetadata.Matches
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.{AntivirusMetadata, FfidMetadata, FileMetadata}
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.{Files, Series, TransferringBody}
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata}

class ValidatorSpec extends ExportSpec {

  private val completeFileMetadata = Files(
    UUID.randomUUID(),
    "File".some,
    "name".some,
    None,
    createMetadata(LocalDateTime.now()),
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

  "extractFFIDMetadata" should "return success if the ffid metadata is present" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val metadata = FileMetadata("ClientSideOriginalFilepath", "filePath") :: Nil
    val ffidMetadata = FfidMetadata("software", "softwareVersion", "binaryVersion", "containerVersion", "method", List(Matches("ext".some, "id", "puid".some)))
    val files = List(Files(fileId,"File".some,"name".some, None, metadata, ffidMetadata.some, Option.empty))
    val result = validator.extractFFIDMetadata(files)
    val expectedResult = ValidatedFFIDMetadata("filePath", "ext", "puid", "software", "softwareVersion", "binaryVersion", "containerVersion")
    result.right.value.head should equal(expectedResult)
  }

  "extractFFIDMetadata" should "return an error if the ffid metadata is missing" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val files = List(Files(fileId, "File".some, "name".some, None, Nil, Option.empty, Option.empty))
    val result = validator.extractFFIDMetadata(files)
    result.left.value.getMessage should equal(s"FFID metadata is missing for file id $fileId")
  }

  "extractFFIDMetadata" should "return an error if the ffid metadata is missing for one file and provided for another" in {
    val validator = Validator(UUID.randomUUID())
    val fileIdOne = UUID.randomUUID()
    val fileIdTwo = UUID.randomUUID()
    val files = List(Files(fileIdOne, "File".some, "name".some, None, Nil, Option.empty, Option.empty), Files(fileIdTwo, "File".some, "name2".some, None, Nil, FfidMetadata("", "", "", "", "", List()).some, Option.empty))
    val result = validator.extractFFIDMetadata(files)
    result.left.value.getMessage should equal(s"FFID metadata is missing for file id $fileIdOne")
  }

  "extractAntivirusMetadata" should "return success if the antivirus metadata is present" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val metadata = FileMetadata("ClientSideOriginalFilepath", "filePath") :: Nil
    val antivirusMetadata = AntivirusMetadata("software", "softwareVersion")
    val files = List(Files(fileId,"File".some, "name".some, None, metadata, Option.empty, antivirusMetadata.some))
    val result = validator.extractAntivirusMetadata(files)
    val expectedResult = ValidatedAntivirusMetadata("filePath", "software", "softwareVersion")
    result.right.value.head should equal(expectedResult)
  }

  "extractAntivirusMetadata" should "return an error if the antivirus metadata is missing" in {
    val validator = Validator(UUID.randomUUID())
    val fileId = UUID.randomUUID()
    val metadata = FileMetadata("ClientSideOriginalFilepath", "filePath") :: Nil
    val files = List(Files(fileId,"File".some, "name".some, None, metadata, Option.empty, Option.empty))
    val result = validator.extractAntivirusMetadata(files)
    result.left.value.getMessage should equal(s"Antivirus metadata is missing for file id $fileId")
  }
}

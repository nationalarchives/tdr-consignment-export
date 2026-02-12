package uk.gov.nationalarchives.consignmentexport

import cats.effect.unsafe.implicits.global
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.ConsignmentMetadata
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.FileMetadata
import uk.gov.nationalarchives.consignmentexport.Utils.PathUtils
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata}
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.io.Source

class BagAdditionalFilesSpec extends ExportSpec {
  "orderedExportProperties" should "return correctly ordered properties to be included in export for a Standard transfer" in {
    val expectedPropertiesOrder: List[String] = List(
      "file_reference","file_name","file_type","file_size","file_path","rights_copyright","legal_status","held_by",
      "date_last_modified","closure_type","closure_start_date","closure_period","foi_exemption_code","foi_exemption_asserted",
      "title_closed","title_alternate","description","description_closed","description_alternate","language","end_date",
      "file_name_translation","original_identifier","parent_reference","former_reference_department","UUID","restrictions_on_use",
      "related_material", "evidence_provided_by", "note", "copyright_details")

    val orderedProperties = BagAdditionalFiles(getClass.getResource(".").getPath.toPath).orderedExportProperties(Standard)
    orderedProperties.size shouldBe expectedPropertiesOrder.size
    val propertiesOrder = orderedProperties.map(_.key)
    propertiesOrder should equal(expectedPropertiesOrder)
  }

  "orderedExportProperties" should "return correctly ordered properties to be included in export for a Judgment transfer" in {
    val expectedPropertiesOrder: List[String] = List(
      "file_reference","file_name","file_type","file_size","file_path","rights_copyright","legal_status","held_by",
      "date_last_modified","closure_type","closure_start_date","closure_period","foi_exemption_code","foi_exemption_asserted",
      "title_closed","title_alternate","description","description_closed","description_alternate","language","end_date",
      "file_name_translation","original_identifier","parent_reference","former_reference_department","UUID","restrictions_on_use"
      ,"related_material","judgment_type", "judgment_update", "judgment_update_type", "judgment_update_details",
      "judgment_neutral_citation","judgment_no_neutral_citation", "judgment_reference", "evidence_provided_by", "note", "copyright_details"
    )

    val orderedProperties = BagAdditionalFiles(getClass.getResource(".").getPath.toPath).orderedExportProperties(Judgment)
    orderedProperties.size shouldBe expectedPropertiesOrder.size
    val propertiesOrder = orderedProperties.map(_.key)
    propertiesOrder should equal(expectedPropertiesOrder)
  }

  "exportValue" should "return empty value when no metadata present for property" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val value = bagAdditionalFiles.exportValue("key", None)
    value should equal("")
  }

  "exportValue" should "return 'data' path value for designated property keys" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val value1 = bagAdditionalFiles.exportValue("file_path", Some("filePathValue"))
    value1 should equal("data/filePathValue")

    val value2 = bagAdditionalFiles.exportValue("original_identifier", Some("filePathValue"))
    value2 should equal("data/filePathValue")
  }

  "exportValue" should "return correctly formatted dates for 'date' type properties" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val dateTimeZeroSeconds = LocalDateTime.parse("2021-02-03T10:33:00.0").format(DateTimeFormatter.ISO_DATE_TIME)
    val value = bagAdditionalFiles.exportValue("date_last_modified", Some(dateTimeZeroSeconds))
    value should equal("2021-02-03T10:33:00")
  }

  "exportValue" should "return the metadata value if property is not a special case" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val value = bagAdditionalFiles.exportValue("file_name", Some("value"))
    value should equal("value")
  }

  "fileMetadataCsv" should "produce a file with the correct rows for a Standard consignment" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val lastModified = LocalDateTime.parse("2021-02-03T10:33:30.414")
    val originalFilePath = "originalFilePath"
    val fileMetadata = createMetadata(lastModified, originalFilePath)

    val validatedFileMetadata = createFile(originalFilePath, fileMetadata, "File", "name", "ref1", Some("ref2"))
    val folderMetadata: List[FileMetadata] = List(
      FileMetadata("Filename", "folderName"),
      FileMetadata("ClientSideOriginalFilepath", "folder"),
      FileMetadata("FileType", "Folder"),
      FileMetadata("FileReference", "folderReference")
    )
    val validatedDirectoryMetadata = createFile(originalFilePath, folderMetadata, "Folder", "folderName", "ref1")
    val file = bagAdditionalFiles.createFileMetadataCsv(Standard, List(validatedFileMetadata, validatedDirectoryMetadata), List.empty).unsafeRunSync()
    val expectedOrderedHeaders = List(
      "file_reference", "file_name", "file_type", "file_size", "clientside_original_filepath", "rights_copyright", "legal_status",
      "held_by", "date_last_modified", "closure_type", "closure_start_date", "closure_period", "foi_exemption_code",
      "foi_exemption_asserted", "title_closed", "title_alternate", "description", "description_closed", "description_alternate",
      "language", "end_date", "file_name_translation", "original_filepath", "parent_reference", "former_reference_department", "UUID",
      "restrictions_on_use", "related_material", "evidence_provided_by", "note", "copyright_details" )
    val expectedOrderedFilePropertyValues = List(
      "fileReference", "fileName", "fileType", "1", "data/originalFilePath", "rightsCopyright", "legalStatus", "heldBy", "2021-02-03T10:33:30",
      "closureType", "2021-02-03T10:33:30", "30", "foiExemption;foiExemption2", "2021-02-03T10:33:30", "titleClosed", "titleAlternate",
      "description", "descriptionClosed", "descriptionAlternate", "language", "2021-02-03T10:33:30", "fileNameTranslation", "data/nonRedactedFilepath",
      "parentReference", "formerReferenceDepartment", "uuid", "restrictions on use", "relatedMaterial", "evidence provided by test", "my note", "my copyright details" )

    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    val header = csvLines.head
    val rest = csvLines.tail
    header.split(",").toList should equal(expectedOrderedHeaders)
    rest.length should equal(2)
    rest.head.split(",").toList should equal(expectedOrderedFilePropertyValues)
    rest.last should equal("folderReference,folderName,Folder,,data/folder,,,,,,,,,,,,,,,,,,,,,,,,,,")
    source.close()
    new File("exporter/src/test/resources/file-metadata.csv").delete()
  }

  "fileMetadataCsv" should "produce a file with the correct rows for a Judgment consignment" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val lastModified = LocalDateTime.parse("2021-02-03T10:33:30.414")
    val originalFilePath = "originalFilePath"
    val fileMetadata = createMetadata(lastModified, originalFilePath)

    val validatedFileMetadata = createFile(originalFilePath, fileMetadata, "File", "name", "ref1", Some("ref2"))
    val consignmentMetadata: List[ConsignmentMetadata] = List(
      ConsignmentMetadata("JudgmentType", "judgmentType"),
      ConsignmentMetadata("JudgmentUpdate", "judgmentUpdate"),
      ConsignmentMetadata("JudgmentUpdateType", "judgmentUpdateType"),
      ConsignmentMetadata("JudgmentUpdateDetails", "judgmentUpdateDetails"),
      ConsignmentMetadata("JudgmentNeutralCitation", "judgmentNeutralCitation"),
      ConsignmentMetadata("JudgmentNoNeutralCitation", "judgmentNoNeutralCitation"),
      ConsignmentMetadata("JudgmentReference", "judgmentReference"),
    )
    val file = bagAdditionalFiles.createFileMetadataCsv(Judgment, List(validatedFileMetadata), consignmentMetadata).unsafeRunSync()
    val expectedOrderedHeaders = List(
      "file_reference", "file_name", "file_type", "file_size", "clientside_original_filepath", "rights_copyright", "legal_status",
      "held_by", "date_last_modified", "closure_type", "closure_start_date", "closure_period", "foi_exemption_code",
      "foi_exemption_asserted", "title_closed", "title_alternate", "description", "description_closed", "description_alternate",
      "language", "end_date", "file_name_translation", "original_filepath", "parent_reference", "former_reference_department", "UUID",
      "restrictions_on_use", "related_material", "judgment_type", "judgment_update", "judgment_update_type", "judgment_update_details",
      "judgment_neutral_citation", "judgment_no_neutral_citation", "judgment_reference", "evidence_provided_by", "note", "copyright_details"
    )
    val expectedOrderedFilePropertyValues = List(
      "fileReference", "fileName", "fileType", "1", "data/originalFilePath", "rightsCopyright", "legalStatus", "heldBy", "2021-02-03T10:33:30",
      "closureType", "2021-02-03T10:33:30", "30", "foiExemption;foiExemption2", "2021-02-03T10:33:30", "titleClosed", "titleAlternate",
      "description", "descriptionClosed", "descriptionAlternate", "language", "2021-02-03T10:33:30", "fileNameTranslation", "data/nonRedactedFilepath",
      "parentReference", "formerReferenceDepartment", "uuid", "restrictions on use", "relatedMaterial", "judgmentType", "judgmentUpdate", "judgmentUpdateType",
      "judgmentUpdateDetails", "judgmentNeutralCitation", "judgmentNoNeutralCitation", "judgmentReference", "evidence provided by test","my note", "my copyright details"
    )

    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    val header = csvLines.head
    val rest = csvLines.tail
    header.split(",").toList should equal(expectedOrderedHeaders)
    rest.length should equal(1)
    rest.head.split(",").toList should equal(expectedOrderedFilePropertyValues)
    source.close()
    new File("exporter/src/test/resources/file-metadata.csv").delete()
  }

  "createFfidMetadataCsv" should "produce a file with the correct rows" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val metadata = ValidatedFFIDMetadata("path", "extension", "puid", "formatName", "false", "software", "softwareVersion", "binarySignatureFileVersion", "containerSignatureFileVersion")

    val file = bagAdditionalFiles.createFfidMetadataCsv(List(metadata)).unsafeRunSync()

    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    val header = csvLines.head
    val rest = csvLines.tail
    header should equal("Filepath,Extension,PUID,FormatName,ExtensionMismatch,FFID-Software,FFID-SoftwareVersion,FFID-BinarySignatureFileVersion,FFID-ContainerSignatureFileVersion")
    rest.length should equal(1)
    rest.head should equal("data/path,extension,puid,formatName,false,software,softwareVersion,binarySignatureFileVersion,containerSignatureFileVersion")
    source.close()
    new File("exporter/src/test/resources/file-metadata.csv").delete()
  }

  "createAntivirusMetadataCsv" should "produce a file with the correct rows" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val validatedAvMetadata = ValidatedAntivirusMetadata("filePath", "software", "softwareVersion")
    val file = bagAdditionalFiles.createAntivirusMetadataCsv(List(validatedAvMetadata)).unsafeRunSync()

    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    val header = csvLines.head
    val rest = csvLines.tail

    header should equal("Filepath,AV-Software,AV-SoftwareVersion")
    rest.length should equal(1)
    rest.head should equal("data/filePath,software,softwareVersion")
    source.close()
    new File(file.getAbsolutePath).delete()
  }
}

package uk.gov.nationalarchives.consignmentexport

import cats.effect.unsafe.implicits.global
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.FileMetadata
import uk.gov.nationalarchives.consignmentexport.ExportSpec.customMetadata
import uk.gov.nationalarchives.consignmentexport.Utils.PathUtils
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata}

import java.io.File
import java.time.LocalDateTime
import scala.io.Source

class BagAdditionalFilesSpec extends ExportSpec {

  "fileMetadataCsv" should "produce a file with the correct rows" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val lastModified = LocalDateTime.parse("2021-02-03T10:33:30.414")
    val originalFilePath = "originalFilePath"
    val fileMetadata = createMetadata(lastModified, originalFilePath)

    val validatedFileMetadata = createFile(originalFilePath, fileMetadata, "File", "name", "ref1", Some("ref2"))
    val folderMetadata: List[FileMetadata] = List(
      FileMetadata("Filename", "folderName"),
      FileMetadata("ClientSideOriginalFilepath", "folder"),
      FileMetadata("FileType", "Folder")
    )
    val validatedDirectoryMetadata = createFile(originalFilePath, folderMetadata, "Folder", "folderName", "ref1")
    val file = bagAdditionalFiles.createFileMetadataCsv(List(validatedFileMetadata, validatedDirectoryMetadata), customMetadata).unsafeRunSync()

    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    val header = csvLines.head
    val rest = csvLines.tail
    header should equal("file_reference,File Path,File Name,File Type,File Size,Rights Copyright,Legal Status,Held By,Language,Last Modified Date,FOI Exemption Code,Checksum,OriginalFilepath,parent_reference")
    rest.length should equal(2)
    rest.head should equal(",data/originalFilePath,File Name,File,1,rightsCopyright,legalStatus,heldBy,language,2021-02-03T10:33:30,foiExemption;foiExemption2,clientSideChecksumValue,data/nonRedactedFilepath,")
    rest.last should equal(s",data/folder,folderName,Folder,,,,,,,,,,")
    source.close()
    new File("exporter/src/test/resources/file-metadata.csv").delete()
  }

  "fileMetadataCsv" should "write the seconds to the file when the input seconds are zero" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val lastModified: LocalDateTime = LocalDateTime.parse("2021-02-03T10:33:00.0")
    val fileMetadata = createMetadata(lastModified)
    val metadata = createFile("originalPath", fileMetadata, "File", "name", "ref1")
    val file = bagAdditionalFiles.createFileMetadataCsv(List(metadata), customMetadata).unsafeRunSync()

    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    val header = csvLines.head
    val rest = csvLines.tail
    header should equal("file_reference,File Path,File Name,File Type,File Size,Rights Copyright,Legal Status,Held By,Language,Last Modified Date,FOI Exemption Code,Checksum,OriginalFilepath,parent_reference")
    rest.length should equal(1)
    rest.head should equal(s",data/originalPath,File Name,File,1,rightsCopyright,legalStatus,heldBy,language,2021-02-03T10:33:00,foiExemption;foiExemption2,clientSideChecksumValue,data/nonRedactedFilepath,")
    source.close()
    new File("exporter/src/test/resources/file-metadata.csv").delete()
  }

  "fileMetadataCsv" should "ignore columns not set for export" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val allowedForExport = customMetadata.head
    val onlyExportFirst = allowedForExport :: customMetadata.tail.map(cm => cm.copy(allowExport = false))
    val fileMetadata = createMetadata(LocalDateTime.now())
    val metadata = createFile("originalPath", fileMetadata, "File", "name", "ref1")

    val file = bagAdditionalFiles.createFileMetadataCsv(List(metadata), onlyExportFirst).unsafeRunSync()

    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    csvLines.head should equal(allowedForExport.fullName.get)
    csvLines.last should equal(fileMetadata.find(_.name == allowedForExport.name).map(_.value).get)
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

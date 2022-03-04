package uk.gov.nationalarchives.consignmentexport

import cats.implicits.catsSyntaxOptionId

import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import uk.gov.nationalarchives.consignmentexport.Utils.PathUtils

import scala.io.Source
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata, ValidatedFileMetadata}

class BagAdditionalFilesSpec extends ExportSpec {
  "fileMetadataCsv" should "produce a file with the correct rows" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val lastModified = LocalDateTime.parse("2021-02-03T10:33:30.414")
    val validatedFileMetadata = ValidatedFileMetadata(
      UUID.randomUUID(),
      "File",
      1L.some,
      lastModified.some,
      "originalPath",
      "foiExemption".some,
      "heldBy".some,
      "language".some,
      "legalStatus".some,
      "rightsCopyright".some,
      "clientSideChecksumValue".some
    )
    val validatedDirectoryMetadata = ValidatedFileMetadata(
      UUID.randomUUID(),
      "Folder",
      None, None,
      "folder",
      None, None, None, None, None, None,
    )
    val file = bagAdditionalFiles.createFileMetadataCsv(List(validatedFileMetadata, validatedDirectoryMetadata)).unsafeRunSync()

    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    val header = csvLines.head
    val rest = csvLines.tail
    header should equal("Filepath,Filesize,RightsCopyright,LegalStatus,HeldBy,Language,FoiExemptionCode,LastModified")
    rest.length should equal(2)
    rest.head should equal(s"data/originalPath,1,rightsCopyright,legalStatus,heldBy,language,foiExemption,2021-02-03T10:33:30")
    rest.last should equal(s"data/folder,,,,,,,")
    source.close()
    new File("exporter/src/test/resources/file-metadata.csv").delete()
  }

  "fileMetadataCsv" should "write the seconds to the file when the input seconds are zero" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val lastModified = LocalDateTime.parse("2021-02-03T10:33:00.0")
    val metadata = ValidatedFileMetadata(
      UUID.randomUUID(),
      "File",
      1L.some,
      lastModified.some,
      "originalPath",
      "foiExemption".some,
      "heldBy".some,
      "language".some,
      "legalStatus".some,
      "rightsCopyright".some,
      "clientSideChecksumValue".some
    )
    val file = bagAdditionalFiles.createFileMetadataCsv(List(metadata)).unsafeRunSync()

    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    val header = csvLines.head
    val rest = csvLines.tail
    header should equal("Filepath,Filesize,RightsCopyright,LegalStatus,HeldBy,Language,FoiExemptionCode,LastModified")
    rest.length should equal(1)
    rest.head should equal(s"data/originalPath,1,rightsCopyright,legalStatus,heldBy,language,foiExemption,2021-02-03T10:33:00")
    source.close()
    new File("exporter/src/test/resources/file-metadata.csv").delete()
  }
  
  "createFfidMetadataCsv" should "produce a file with the correct rows" in {
    val bagAdditionalFiles = BagAdditionalFiles(getClass.getResource(".").getPath.toPath)
    val metadata = ValidatedFFIDMetadata("path", "extension", "puid", "software", "softwareVersion", "binarySignatureFileVersion", "containerSignatureFileVersion")
    
    val file = bagAdditionalFiles.createFfidMetadataCsv(List(metadata)).unsafeRunSync()
    
    val source = Source.fromFile(file)
    val csvLines = source.getLines().toList
    val header = csvLines.head
    val rest = csvLines.tail
    header should equal("Filepath,Extension,PUID,FFID-Software,FFID-SoftwareVersion,FFID-BinarySignatureFileVersion,FFID-ContainerSignatureFileVersion")
    rest.length should equal(1)
    rest.head should equal("data/path,extension,puid,software,softwareVersion,binarySignatureFileVersion,containerSignatureFileVersion")
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

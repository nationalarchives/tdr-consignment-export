package uk.gov.nationalarchives.consignmentexport

import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files

import java.nio.file.Path
import cats.effect.IO
import com.github.tototoshi.csv.CSVWriter
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata}

import java.io.File
import java.time.format.DateTimeFormatter

class BagAdditionalFiles(rootDirectory: Path) {

  def createAntivirusMetadataCsv(validatedAntivirusMetadata: List[ValidatedAntivirusMetadata]): IO[File] = {
    val header = List("Filepath", "AV-Software", "AV-SoftwareVersion")
    val avMetadataRows = validatedAntivirusMetadata.map(av => List(dataPath(av.filePath), av.software, av.softwareVersion))
    writeToCsv("file-av.csv", header, avMetadataRows)
  }

  def createFileMetadataCsv(files: List[Files]): IO[File] = {
    val header = List("Filepath", "FileName", "FileType", "Filesize", "RightsCopyright", "LegalStatus", "HeldBy", "Language", "FoiExemptionCode", "LastModified", "OriginalFilePath")

    val fileMetadataRows = files.map(f => {
      val metadata = f.metadata
      List(
        dataPath(f.metadata.clientSideOriginalFilePath.getOrElse("")),
        f.fileName.getOrElse(""),
        f.fileType.getOrElse(""),
        metadata.clientSideFileSize.getOrElse(""),
        metadata.rightsCopyright.getOrElse(""),
        metadata.legalStatus.getOrElse(""),
        metadata.heldBy.getOrElse(""),
        metadata.language.getOrElse(""),
        metadata.foiExemptionCode.getOrElse(""),
        metadata.clientSideLastModifiedDate.map(d => DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(d)).getOrElse(""),
        f.originalFilePath.map(dataPath).getOrElse("")
      )
    })
    writeToCsv("file-metadata.csv", header, fileMetadataRows)
  }

  def createFfidMetadataCsv(ffidMetadataList: List[ValidatedFFIDMetadata]): IO[File] = {
    val header = List("Filepath","Extension","PUID","FFID-Software","FFID-SoftwareVersion","FFID-BinarySignatureFileVersion","FFID-ContainerSignatureFileVersion")
    val metadataRows = ffidMetadataList.map(f => {
      List(dataPath(f.filePath), f.extension, f.puid, f.software, f.softwareVersion, f.binarySignatureFileVersion, f.containerSignatureFileVersion)
    })
    writeToCsv("file-ffid.csv", header, metadataRows)
  }

  private def dataPath(filePath: String): String = s"data/$filePath"

  private def writeToCsv(fileName: String, header: List[String], metadataRows: List[List[Any]]): IO[File] = {
    val file = new File(s"$rootDirectory/$fileName")
    val writer = CSVWriter.open(file)
    writer.writeAll(header :: metadataRows)
    writer.close()
    IO(file)
  }
}

object BagAdditionalFiles {
  def apply(rootDirectory: Path): BagAdditionalFiles = new BagAdditionalFiles(rootDirectory)
}

package uk.gov.nationalarchives.consignmentexport

import java.io.File
import java.nio.file.Path
import cats.effect.IO
import com.github.tototoshi.csv.CSVWriter
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata, ValidatedFileMetadata}

import java.time.format.DateTimeFormatter

class BagAdditionalFiles(rootDirectory: Path) {

  def createAntivirusMetadataCsv(validatedAntivirusMetadata: List[ValidatedAntivirusMetadata]): IO[File] = {
    val header = List("Filepath", "AV-Software", "AV-SoftwareVersion")
    val avMetadataRows = validatedAntivirusMetadata.map(av => List(dataPath(av.filePath), av.software, av.softwareVersion))
    writeToCsv("file-av.csv", header, avMetadataRows)
  }

  def createFileMetadataCsv(fileMetadataList: List[ValidatedFileMetadata]): IO[File] = {
    val header = List("Filepath", "FileName", "FileType", "Filesize", "RightsCopyright", "LegalStatus", "HeldBy", "Language", "FoiExemptionCode", "LastModified")
    val fileMetadataRows = fileMetadataList.map(f => List(
      dataPath(f.clientSideOriginalFilePath),
      f.fileName,
      f.fileType,
      f.clientSideFileSize.getOrElse(""),
      f.rightsCopyright.getOrElse(""),
      f.legalStatus.getOrElse(""),
      f.heldBy.getOrElse(""),
      f.language.getOrElse(""),
      f.foiExemptionCode.getOrElse(""),
      f.clientSideLastModifiedDate.map(d => DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(d)).getOrElse(""),
    )
    )
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

package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import com.github.tototoshi.csv.CSVWriter
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata}

import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BagAdditionalFiles(rootDirectory: Path) {

  def createAntivirusMetadataCsv(validatedAntivirusMetadata: List[ValidatedAntivirusMetadata]): IO[File] = {
    val header = List("Filepath", "AV-Software", "AV-SoftwareVersion")
    val avMetadataRows = validatedAntivirusMetadata.map(av => List(dataPath(av.filePath), av.software, av.softwareVersion))
    writeToCsv("file-av.csv", header, avMetadataRows)
  }

  def createFileMetadataCsv(files: List[Files], customMetadata: List[CustomMetadata]): IO[File] = {
    val parseFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm:ss[.SSS][.SS][.S]")
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    val filteredMetadata: List[CustomMetadata] = customMetadata.filter(_.allowExport).sortBy(_.exportOrdinal.getOrElse(Int.MaxValue))
    val header: List[String] = filteredMetadata.map(f => f.fullName.getOrElse(f.name))
    val fileMetadataRows: List[List[String]] = files.map(file => {
      val groupedMetadata = file.fileMetadata.groupBy(_.name).view.mapValues(_.map(_.value).mkString("|")).toMap
      filteredMetadata.map(customMetadata => groupedMetadata.get(customMetadata.name).map(fileMetadataValue => {
        if(customMetadata.name == "ClientSideOriginalFilepath" || customMetadata.name == "OriginalFilepath") {
          dataPath(fileMetadataValue)
        } else if(filteredMetadata.find(_.name == customMetadata.name).exists(_.dataType == DataType.DateTime)) {
          LocalDateTime.parse(fileMetadataValue, parseFormatter).format(formatter)
        } else {
          fileMetadataValue
        }
      }).getOrElse(""))
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

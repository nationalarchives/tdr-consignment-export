package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import com.github.tototoshi.csv.CSVWriter
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.FileMetadata
import uk.gov.nationalarchives.consignmentexport.BagAdditionalFiles.MetadataConfiguration
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata}
import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils
import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils.DownloadFileDisplayProperty

import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BagAdditionalFiles(rootDirectory: Path, metadataConfig: MetadataConfiguration) {
  def createAntivirusMetadataCsv(validatedAntivirusMetadata: List[ValidatedAntivirusMetadata]): IO[File] = {
    val header = List("Filepath", "AV-Software", "AV-SoftwareVersion")
    val avMetadataRows = validatedAntivirusMetadata.map(av => List(dataPath(av.filePath), av.software, av.softwareVersion))
    writeToCsv("file-av.csv", header, avMetadataRows)
  }

  def createFileMetadataCsv(files: List[Files]): IO[File] = {
    val orderedProperties = orderedExportProperties()

    val dataHeaderMapper = metadataConfig.dataHeaderMapper

    val fileMetadataRows: List[List[String]] = files.map(file => {
      orderedProperties.map(op => {
        val key = op.key
        val metadataHeader = dataHeaderMapper(key)
        val metadata: List[FileMetadata] = file.fileMetadata.filter(_.name == metadataHeader)
        exportValue(key, metadata)
      })
    })

    val header: List[String] = orderedProperties.map(op => metadataConfig.exportHeaderMapper(op.key))
    writeToCsv("file-metadata.csv", header, fileMetadataRows)
  }

  def createFfidMetadataCsv(ffidMetadataList: List[ValidatedFFIDMetadata]): IO[File] = {
    val header = List("Filepath", "Extension", "PUID", "FormatName", "ExtensionMismatch", "FFID-Software", "FFID-SoftwareVersion", "FFID-BinarySignatureFileVersion", "FFID-ContainerSignatureFileVersion")
    val metadataRows = ffidMetadataList.map(f => {
      List(dataPath(f.filePath), f.extension, f.puid, f.formatName, f.extensionMismatch, f.software, f.softwareVersion, f.binarySignatureFileVersion, f.containerSignatureFileVersion)
    })
    writeToCsv("file-ffid.csv", header, metadataRows)
  }

  def orderedExportProperties(): List[DownloadFileDisplayProperty] = {
    metadataConfig.exportDisplayProperties
      .filter(dlp => metadataConfig.exportProperties(dlp.key) == "true")
      .sortBy(_.columnIndex)
  }

  def exportValue(propertyKey: String, metadata: List[FileMetadata]): String = {
    lazy val propertyType = metadataConfig.propertyTypeEvaluator(propertyKey)
    lazy val parseFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm:ss[.SSS][.SS][.S]")
    lazy val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    if (metadata.isEmpty) { "" } else {
      val metadataValue = metadata.head.value
      propertyKey match {
        case pk if pk == "file_path" || pk == "original_identifier" => dataPath(metadataValue)
        case _ if propertyType == "date" => LocalDateTime.parse(metadataValue, parseFormatter).format(formatter)
        case _ => metadataValue
      }
    }
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
  private val config = ConfigUtils.loadConfiguration
  private val propertyTypeEvaluator = config.getPropertyType
  private val exportProperties = config.propertyToOutputMapper("allowExport")
  private val exportDisplayProperties = config.downloadFileDisplayProperties("BagitExportTemplate")
  private val exportHeaderMapper = config.propertyToOutputMapper("tdrBagitExportHeader")
  private val dataHeaderMapper = config.propertyToOutputMapper("tdrDataLoadHeader")

  private val metadataConfig = MetadataConfiguration(propertyTypeEvaluator, exportDisplayProperties, exportProperties, exportHeaderMapper, dataHeaderMapper)

  def apply(rootDirectory: Path): BagAdditionalFiles = new BagAdditionalFiles(rootDirectory, metadataConfig)

  case class MetadataConfiguration(
                                    propertyTypeEvaluator: String => String,
                                    exportDisplayProperties: List[DownloadFileDisplayProperty],
                                    exportProperties: String => String,
                                    exportHeaderMapper: String => String,
                                    dataHeaderMapper: String => String
                                  )
}

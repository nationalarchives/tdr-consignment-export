package uk.gov.nationalarchives.consignmentexport

import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files

import java.nio.file.{Path, Paths}
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object Utils {
  implicit class PathUtils(str: String) {
    def toPath: Path = Paths.get(str)
  }

  implicit class ZonedDatetimeUtils(value: ZonedDateTime) {
    def toFormattedPrecisionString: String = {
      value.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
  }

  implicit class FileMetadataHelper(files: Files) {
    private val fileMetadata = files.fileMetadata
    val directoryType = "Folder"

    def getClientSideOriginalFilePath: String = {
      fileMetadata.find(_.name == "ClientSideOriginalFilepath").map(_.value).getOrElse("")
    }

    def getSha256ClientSideChecksum: String = {
      fileMetadata.find(_.name == "SHA256ClientSideChecksum").map(_.value).getOrElse("")
    }

    def isFolder: Boolean = {
      files.fileType.contains(directoryType)
    }
  }
}

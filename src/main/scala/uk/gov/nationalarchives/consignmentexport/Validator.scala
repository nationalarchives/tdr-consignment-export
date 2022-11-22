package uk.gov.nationalarchives.consignmentexport

import cats.implicits._
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import uk.gov.nationalarchives.consignmentexport.Utils.FileMetadataHelper
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata}

import java.util.UUID

class Validator(consignmentId: UUID) {
  def validateConsignmentHasFiles(consignmentData: GetConsignment): Either[RuntimeException, Files] = {
    Either.fromOption(consignmentData.files.headOption, new RuntimeException(s"Consignment API returned no files for consignment $consignmentId"))
  }

  def validateConsignmentResult(consignmentResult: Option[GetConsignment]): Either[RuntimeException, GetConsignment] = {
    Either.fromOption(consignmentResult, new RuntimeException(s"No consignment metadata found for consignment $consignmentId"))
  }

  def extractFFIDMetadata(filesList: List[Files]): Either[RuntimeException, List[ValidatedFFIDMetadata]] = {
    val fileErrors = filesList.filter(file => file.ffidMetadata.isEmpty && !file.isFolder)
      .map(f => s"FFID metadata is missing for file id ${f.fileId}")
    fileErrors match {
      case Nil => Right(filesList.filter(!_.isFolder).flatMap(file => {
        val metadata = file.ffidMetadata.get
        metadata.matches.map(mm => {
          ValidatedFFIDMetadata(file.getClientSideOriginalFilePath, mm.extension.getOrElse(""), mm.puid.getOrElse(""), metadata.software, metadata.softwareVersion, metadata.binarySignatureFileVersion, metadata.containerSignatureFileVersion)
        })
      }))
      case _ => Left(new RuntimeException(fileErrors.mkString("\n")))
    }
  }

  def extractAntivirusMetadata(filesList: List[Files]): Either[RuntimeException, List[ValidatedAntivirusMetadata]] = {
    val fileErrors = filesList.filter(file => file.antivirusMetadata.isEmpty && !file.isFolder)
      .map(f => s"Antivirus metadata is missing for file id ${f.fileId}")
    fileErrors match {
      case Nil => Right(
        filesList.filter(!_.isFolder).map(f => {
          val antivirus = f.antivirusMetadata.get
          ValidatedAntivirusMetadata(f.getClientSideOriginalFilePath, antivirus.software, antivirus.softwareVersion)
        })
      )
      case _ => Left(new RuntimeException(fileErrors.mkString("\n")))
    }
  }
}

object Validator {

  case class ValidatedFFIDMetadata(filePath: String,
                                   extension: String,
                                   puid: String,
                                   software: String,
                                   softwareVersion: String,
                                   binarySignatureFileVersion: String,
                                   containerSignatureFileVersion: String)

  case class ValidatedAntivirusMetadata(filePath: String,
                                        software: String,
                                        softwareVersion: String)

  def apply(consignmentId: UUID): Validator = new Validator(consignmentId)
}

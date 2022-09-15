package uk.gov.nationalarchives.consignmentexport

import cats.implicits._
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import uk.gov.nationalarchives.consignmentexport.Main.directoryType
import uk.gov.nationalarchives.consignmentexport.Validator.{ValidatedAntivirusMetadata, ValidatedFFIDMetadata, ValidatedFileMetadata}

import java.time.LocalDateTime
import java.util.UUID

class Validator(consignmentId: UUID) {
  def validateConsignmentHasFiles(consignmentData: GetConsignment): Either[RuntimeException, Files] = {
    Either.fromOption(consignmentData.files.headOption, new RuntimeException(s"Consignment API returned no files for consignment $consignmentId"))
  }

  def validateConsignmentResult(consignmentResult: Option[GetConsignment]): Either[RuntimeException, GetConsignment] = {
    Either.fromOption(consignmentResult, new RuntimeException(s"No consignment metadata found for consignment $consignmentId"))
  }

  def extractFileMetadata(filesList: List[Files]): Either[RuntimeException, List[ValidatedFileMetadata]] = {
    val fileErrors: Seq[String] = filesList
      .filter(f => f.fileType.isDefined && f.fileType.get != directoryType)
      .flatMap(file => {
      val metadataPropertyNames = file.metadata.productElementNames
      val metadataValues = file.metadata.productIterator
      val missingPropertyNames = metadataPropertyNames.zip(metadataValues).filter(propertyNameToValue => {
        propertyNameToValue._2.isInstanceOf[None.type]
      }).map(_._1).toList

      missingPropertyNames match {
        case Nil => None
        case _ => s"${file.fileId} is missing the following properties: ${missingPropertyNames.mkString(", ")}".some
      }
    })
    val directoryErrors = filesList
      .filter(f => f.fileType.isDefined && f.fileType.get != directoryType)
      .filter(_.metadata.clientSideOriginalFilePath.isEmpty)
      .map(_ => "ClientSideOriginalFilePath")

    directoryErrors ++ fileErrors match {
      case Nil => Right(filesList.map(validatedMetadata))
      case _ => Left(new RuntimeException(fileErrors.mkString("\n")))
    }
  }

  def extractFFIDMetadata(filesList: List[Files]): Either[RuntimeException, List[ValidatedFFIDMetadata]] = {
    val fileErrors = filesList.filter(file => file.ffidMetadata.isEmpty && file.fileType.get != directoryType)
      .map(f => s"FFID metadata is missing for file id ${f.fileId}")
    fileErrors match {
      case Nil => Right(filesList.filter(_.fileType.get != directoryType).flatMap(file => {
        val metadata = file.ffidMetadata.get
        metadata.matches.map(mm => {
          ValidatedFFIDMetadata(file.metadata.clientSideOriginalFilePath.get, mm.extension.getOrElse(""), mm.puid.getOrElse(""), metadata.software, metadata.softwareVersion, metadata.binarySignatureFileVersion, metadata.containerSignatureFileVersion)
        })
      }))
      case _ => Left(new RuntimeException(fileErrors.mkString("\n")))
    }
  }

  def extractAntivirusMetadata(filesList: List[Files]): Either[RuntimeException, List[ValidatedAntivirusMetadata]] = {
    val fileErrors = filesList.filter(file => file.antivirusMetadata.isEmpty && file.fileType.get != directoryType)
      .map(f => s"Antivirus metadata is missing for file id ${f.fileId}")
    fileErrors match {
      case Nil => Right(
        filesList.filter(_.fileType.get != directoryType).map(f => {
          val antivirus = f.antivirusMetadata.get
          ValidatedAntivirusMetadata(f.metadata.clientSideOriginalFilePath.get, antivirus.software, antivirus.softwareVersion)
        })
      )
      case _ => Left(new RuntimeException(fileErrors.mkString("\n")))
    }
  }

  private def validatedMetadata(f: Files): ValidatedFileMetadata =
    ValidatedFileMetadata(f.fileId,
    f.fileName.get,
    f.fileType.get,
    f.metadata.clientSideFileSize,
    f.metadata.clientSideLastModifiedDate,
    f.metadata.clientSideOriginalFilePath.get,
    f.metadata.foiExemptionCode,
    f.metadata.heldBy,
    f.metadata.language,
    f.metadata.legalStatus,
    f.metadata.rightsCopyright,
    f.metadata.sha256ClientSideChecksum,
    f.originalFilePath
  )

}

object Validator {

  case class ValidatedFFIDMetadata(filePath: String,
                                   extension: String,
                                   puid: String,
                                   software: String,
                                   softwareVersion: String,
                                   binarySignatureFileVersion: String,
                                   containerSignatureFileVersion: String)

  case class ValidatedFileMetadata(fileId: UUID,
                                   fileName: String,
                                   fileType: String,
                                   clientSideFileSize: Option[Long],
                                   clientSideLastModifiedDate: Option[LocalDateTime],
                                   clientSideOriginalFilePath: String,
                                   foiExemptionCode: Option[String],
                                   heldBy: Option[String],
                                   language: Option[String],
                                   legalStatus: Option[String],
                                   rightsCopyright: Option[String],
                                   clientSideChecksum: Option[String],
                                   originalFile: Option[String]
                                  )

  case class ValidatedAntivirusMetadata(filePath: String,
                                        software: String,
                                        softwareVersion: String)

  def apply(consignmentId: UUID): Validator = new Validator(consignmentId)
}

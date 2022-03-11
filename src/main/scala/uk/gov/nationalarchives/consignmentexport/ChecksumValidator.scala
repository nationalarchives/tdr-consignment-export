package uk.gov.nationalarchives.consignmentexport

import java.util.UUID

import gov.loc.repository.bagit.domain.Bag
import uk.gov.nationalarchives.consignmentexport.Main.directoryType
import uk.gov.nationalarchives.consignmentexport.Validator.ValidatedFileMetadata

import scala.jdk.CollectionConverters._

class ChecksumValidator() {

  def findChecksumMismatches(bag: Bag, validatedFileMetadata: List[ValidatedFileMetadata]): List[UUID] = {
    val bagitGeneratedChecksums = bag.getPayLoadManifests.asScala.head.getFileToChecksumMap.values

    validatedFileMetadata
      .filter(_.fileType != directoryType)
      .filterNot(fm => bagitGeneratedChecksums.contains(fm.clientSideChecksum.getOrElse("")))
      .map(_.fileId)
  }
}

object ChecksumValidator {

  def apply(): ChecksumValidator = new ChecksumValidator()
}

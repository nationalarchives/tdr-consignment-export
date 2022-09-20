package uk.gov.nationalarchives.consignmentexport

import gov.loc.repository.bagit.domain.Bag
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import uk.gov.nationalarchives.consignmentexport.Utils.FileMetadataHelper

import java.util.UUID
import scala.jdk.CollectionConverters._

class ChecksumValidator() {

  def findChecksumMismatches(bag: Bag, validatedFileMetadata: List[Files]): List[UUID] = {
    val bagitGeneratedChecksums = bag.getPayLoadManifests.asScala.head.getFileToChecksumMap.values

    validatedFileMetadata
      .filter(!_.isFolder())
      .filterNot(fm => bagitGeneratedChecksums.contains(fm.getSha256ClientSideChecksum))
      .map(_.fileId)
  }
}

object ChecksumValidator {

  def apply(): ChecksumValidator = new ChecksumValidator()
}

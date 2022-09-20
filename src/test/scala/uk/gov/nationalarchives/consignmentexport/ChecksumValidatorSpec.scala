package uk.gov.nationalarchives.consignmentexport

import cats.implicits.catsSyntaxOptionId

import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.UUID
import gov.loc.repository.bagit.domain.Version.LATEST_BAGIT_VERSION
import gov.loc.repository.bagit.domain._
import gov.loc.repository.bagit.hash.StandardSupportedAlgorithms
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.Metadata

import scala.jdk.CollectionConverters._
import scala.util.Random

class ChecksumValidatorSpec extends ExportSpec {

  private val fileId1 = UUID.randomUUID()
  private val fileId2 = UUID.randomUUID()
  private val fileId3 = UUID.randomUUID()

  private val metadata1 = createFiles(fileId1,"clientSideChecksum1")
  private val metadata2 = createFiles(fileId2,"clientSideChecksum2")
  private val metadata3 = createFiles(fileId3, "clientSideChecksum3")

  "findChecksumMismatches" should "should return empty list if no checksum mismatches" in {
    val checksums = List(
      "clientSideChecksum1",
      "clientSideChecksum2",
      "clientSideChecksum3"
    )

    val checksumMismatches = ChecksumValidator().findChecksumMismatches(createBag(checksums), List(metadata1, metadata2, metadata3))
    checksumMismatches.isEmpty should be(true)
  }

  "findChecksumMismatches" should "return a list of the file ids where a checksum mismatch was found" in {
    val checksums = List(
      "clientSideChecksum1",
      "someDifferentClientSideChecksum1",
      "someDifferentClientSideChecksum2"
    )

    val checksumMismatches = ChecksumValidator().findChecksumMismatches(createBag(checksums), List(metadata1, metadata2, metadata3))
    checksumMismatches.size should be(2)
    checksumMismatches.contains(fileId2) should be(true)
    checksumMismatches.contains(fileId3) should be(true)
  }

 private def createFiles(fileId: UUID, checksumValue: String): Files = {
  val metadata = createMetadata(LocalDateTime.parse("2021-02-03T10:33:30.414"), checkSum = checksumValue)
  Files(fileId, "File".some, "name".some, None, metadata, None, None)
  }

  private def createBag(checksums: List[String]): Bag = {
    val pathToChecksums = checksums.map(cs => Paths.get(Random.alphanumeric.take(4).mkString("")) -> cs).toMap
    val bagManifest = new Manifest(StandardSupportedAlgorithms.SHA256)
    bagManifest.setFileToChecksumMap(pathToChecksums.asJava)

    val bag = new Bag(LATEST_BAGIT_VERSION())
    bag.setPayLoadManifests(Set(bagManifest).asJava)

    bag
  }
}

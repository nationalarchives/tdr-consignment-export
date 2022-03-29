package uk.gov.nationalarchives.consignmentexport

import java.io.File
import cats.effect.unsafe.implicits.global

class ChecksumCalculatorSpec extends ExportSpec {
  "calculateChecksum" should "calculate the checksum for a single file" in {
    val file = new File(getClass.getResource(s"/testfiles/testfile").getPath)
    val files = ChecksumCalculator().calculateChecksums(file).unsafeRunSync()
    files.length should equal(1)
    files.head.checksum should equal("cd0aa9856147b6c5b4ff2b7dfee5da20aa38253099ef1b4a64aced233c9afe29")
  }

  "calculateChecksum" should "calculate the checksum for a multiple files" in {
    val fileOne = new File(getClass.getResource(s"/testfiles/testfile").getPath)
    val fileTwo = new File(getClass.getResource(s"/testfiles/testfile2").getPath)
    val files = ChecksumCalculator().calculateChecksums(fileOne, fileTwo).unsafeRunSync()
    files.length should equal(2)
    files.head.checksum should equal("cd0aa9856147b6c5b4ff2b7dfee5da20aa38253099ef1b4a64aced233c9afe29")
    files.last.checksum should equal("705ce8819a3dc40a54c45c3cc353413421a29667dd79c240b982a4bea1e2b651")
  }
}

package uk.gov.nationalarchives.consignmentexport

import java.io.File
import java.nio.file.{Files, StandardCopyOption}
import cats.effect.unsafe.implicits.global
import scala.util.Using

class ChecksumCalculatorSpec extends ExportSpec {
  private def withTempFileFromResource[T](resourcePath: String)(test: File => T): T = {
    val tempFile = Files.createTempFile("checksum-calculator-spec", ".tmp")
    try {
      val inputStream = Option(getClass.getResourceAsStream(resourcePath))
        .getOrElse(throw new RuntimeException(s"Missing test resource $resourcePath"))
      Using.resource(inputStream) { stream =>
        Files.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING)
      }
      test(tempFile.toFile)
    } finally {
      Files.deleteIfExists(tempFile)
    }
  }

  "calculateChecksum" should "calculate the checksum for a single file" in {
    withTempFileFromResource("/testfiles/testfile") { file =>
      val files = ChecksumCalculator().calculateChecksums(file).unsafeRunSync()
      files.length should equal(1)
      files.head.checksum should equal("cd0aa9856147b6c5b4ff2b7dfee5da20aa38253099ef1b4a64aced233c9afe29")
    }
  }

  "calculateChecksum" should "calculate the checksum for a multiple files" in {
    withTempFileFromResource("/testfiles/testfile") { fileOne =>
      withTempFileFromResource("/testfiles/testfile2") { fileTwo =>
        val files = ChecksumCalculator().calculateChecksums(fileOne, fileTwo).unsafeRunSync()
        files.length should equal(2)
        files.head.checksum should equal("cd0aa9856147b6c5b4ff2b7dfee5da20aa38253099ef1b4a64aced233c9afe29")
        files.last.checksum should equal("705ce8819a3dc40a54c45c3cc353413421a29667dd79c240b982a4bea1e2b651")
      }
    }
  }
}

package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment.Files.Metadata
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.mockito.scalatest.MockitoSugar
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.time.LocalDateTime

abstract class ExportSpec extends AnyFlatSpec with MockitoSugar with Matchers with EitherValues {
  implicit def logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  def createMetadata(
                      lastModified: LocalDateTime,
                      originalPath: String = "originalPath",
                      checkSum: String = "clientSideChecksumValue"): Metadata = {
    Metadata(
      1L.some,
      lastModified.some,
      originalPath.some,
      "foiExemption".some,
      "heldBy".some,
      "language".some,
      "legalStatus".some,
      "rightsCopyright".some,
      checkSum.some)
  }
}

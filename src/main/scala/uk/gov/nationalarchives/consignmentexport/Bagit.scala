package uk.gov.nationalarchives.consignmentexport

import java.nio.charset.Charset
import java.nio.file.Path
import java.util
import cats.effect.IO
import gov.loc.repository.bagit.creator.BagCreator
import gov.loc.repository.bagit.domain.{Bag, Manifest, Metadata}
import gov.loc.repository.bagit.hash.{StandardSupportedAlgorithms, SupportedAlgorithm}
import gov.loc.repository.bagit.verify.BagVerifier
import gov.loc.repository.bagit.writer.ManifestWriter
import org.typelevel.log4cats.SelfAwareStructuredLogger
import uk.gov.nationalarchives.consignmentexport.ChecksumCalculator.ChecksumFile
import uk.gov.nationalarchives.consignmentexport.Utils._

import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class Bagit(bagInPlace: (Path, util.Collection[SupportedAlgorithm], Boolean, Metadata) => Bag,
            validateBag: (Bag, Boolean) => Unit,
            writeTagManifests: (util.Set[Manifest], Path, Path, Charset) => Unit
           )(implicit val logger: SelfAwareStructuredLogger[IO]) {

  def createBag(consignmentReference: String, rootLocation: String, metadata: Metadata): IO[Bag] = for {
    bag <- IO(bagInPlace(
      s"$rootLocation/$consignmentReference".toPath,
      List(StandardSupportedAlgorithms.SHA256: SupportedAlgorithm).asJavaCollection,
      true,
      metadata))
    _ <- IO(validateBag(bag, true))
    _ <- logger.info(s"Bagit export complete for consignment $consignmentReference")
  } yield bag

  def writeTagManifestRows(bag: Bag, checksumFiles: List[ChecksumFile]): IO[Unit] = IO {
    val fileToChecksumMap: util.Map[Path, String] = checksumFiles.map(f => f.file.toPath -> f.checksum).toMap.asJava
    bag.getTagManifests.asScala.head.getFileToChecksumMap.putAll(fileToChecksumMap)
    writeTagManifests.apply(bag.getTagManifests, bag.getRootDir, bag.getRootDir, bag.getFileEncoding)
  }
}

object Bagit {
  //Passing the method value to the class to make unit testing possible as there's no easy way to mock the file writing
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]): Bagit =
    new Bagit(BagCreator.bagInPlace, new BagVerifier().isComplete, ManifestWriter.writeTagManifests)(logger)
}

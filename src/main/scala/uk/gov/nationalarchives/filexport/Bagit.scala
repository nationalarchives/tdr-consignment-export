package uk.gov.nationalarchives.filexport

import java.nio.file.Path
import java.util
import java.util.UUID

import cats.effect.IO
import gov.loc.repository.bagit.creator.BagCreator
import gov.loc.repository.bagit.hash.{StandardSupportedAlgorithms, SupportedAlgorithm}
import gov.loc.repository.bagit.verify.BagVerifier
import gov.loc.repository.bagit.writer.BagWriter
import uk.gov.nationalarchives.filexport.Config._
import Utils._
import gov.loc.repository.bagit.domain.Bag
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger

import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.Try

class Bagit(bagInPlace: (Path, util.Collection[SupportedAlgorithm], Boolean) => Bag,
           isComplete: (Bag, Boolean) => Unit)(implicit val logger: SelfAwareStructuredLogger[IO]) {

  def createBag(consignmentId: UUID, rootLocation: String, includeHiddenFiles: Boolean): IO[Unit] = for {
    bag <- IO(bagInPlace(s"$rootLocation/$consignmentId".toPath, List(StandardSupportedAlgorithms.SHA256: SupportedAlgorithm).asJavaCollection, includeHiddenFiles))
    _ <- IO.pure(isComplete(bag, includeHiddenFiles))
    _ <- logger.info("Bagit export complete")
  } yield ()
}

object Bagit {
  //Passing the method value to the class to make unit testing possible as there's no easy way to mock the file writing
  def apply()(implicit logger: SelfAwareStructuredLogger[IO]): Bagit = new Bagit(BagCreator.bagInPlace, new BagVerifier().isComplete)(logger)
}

package uk.gov.nationalarchives.consignmentexport

import cats.effect.IO
import org.typelevel.log4cats.SelfAwareStructuredLogger

import java.io.File

object FileUtils {
  def deleteDownloadDirectories(rootLocation: String)(implicit logger: SelfAwareStructuredLogger[IO]): IO[Unit] = {
    val file = new File(rootLocation)

    def deleteRecursively(file: File): Unit = {
      if (file.isDirectory) {
        file.listFiles.foreach(deleteRecursively)
      }
      if (file.exists && !file.delete) {
        IO.raiseError(throw new Exception(s"Unable to delete ${file.getAbsolutePath}"))
      }
    }

    for {
      _ <- logger.info(s"Deleting directory $rootLocation from EFS")
      _ <- IO.blocking(deleteRecursively(file)).onError(e => logger.error(e.getMessage))
    } yield ()
  }
}

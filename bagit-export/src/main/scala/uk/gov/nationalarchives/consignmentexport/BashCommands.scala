package uk.gov.nationalarchives.consignmentexport

import java.io.{File, FileOutputStream}
import java.nio.charset.Charset

import cats.effect.{IO, Resource}
import org.typelevel.log4cats.SelfAwareStructuredLogger

import scala.language.postfixOps
import scala.sys.process._

class BashCommands(outputToFile: (String, File) => IO[Unit])(implicit val logger: SelfAwareStructuredLogger[IO]) {

  def runCommand(command: String): IO[String] = for {
    output <- IO.blocking(Seq("sh", "-c", command) !!)
    _ <- logger.info(s"$command has been run")
  } yield output

  def runCommandToFile(command: String, file: File): IO[Unit] = for {
    output <- runCommand(command)
    _ <- outputToFile(output, file)
    _ <- logger.info(s"command $command run and saved to ${file.getName}")
  } yield ()
}

object BashCommands {
  private def outputToFile: (String, File) => IO[Unit] = (output, file) =>
    Resource.make {
      IO.blocking(new FileOutputStream(file))
    } { outStream =>
      IO.blocking(outStream.close()).handleErrorWith(_ => IO.unit)
    }.use(fos =>
      IO.blocking(fos.write(output.getBytes(Charset.forName("UTF-8")))))

  def apply()(implicit logger: SelfAwareStructuredLogger[IO]): BashCommands = new BashCommands(outputToFile)(logger)
}

package uk.gov.nationalarchives.consignmentexport

import java.io.File

import cats.effect.IO
import org.mockito.ArgumentCaptor
import cats.effect.unsafe.implicits.global

class BashCommandsSpec extends ExportSpec {
  "the runCommand method" should "returns the correct output for a successful command" in {
    val exitCode = BashCommands().runCommand("echo 'hello'")
    exitCode.unsafeRunSync() should equal("hello\n")
  }

  "the runCommand method" should "throw an error for a failed command" in {
    val exception = intercept[RuntimeException] {
      BashCommands().runCommand("invalidcommand").unsafeRunSync()
    }
    exception.getMessage should equal("Nonzero exit value: 127")
  }

  "the runCommandWithFile method" should "call the filewriter function with the correct arguments" in {
    val fileWriterMock: (String, File) => IO[Unit] = mock[(String, File) => IO[Unit]]
    val commandCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
    val fileCaptor: ArgumentCaptor[File] = ArgumentCaptor.forClass(classOf[File])
    doAnswer(() => IO.unit).when(fileWriterMock).apply(commandCaptor.capture(), fileCaptor.capture())
    new BashCommands(fileWriterMock).runCommandToFile("echo 'hello'", new File("test")).unsafeRunSync()
    commandCaptor.getValue should equal("hello\n")
    fileCaptor.getValue.getName should equal("test")
  }
}

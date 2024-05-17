package uk.gov.nationalarchives.consignmentexport

import java.io.{File, FileInputStream}

import cats.effect.{IO, Resource}
import cats.implicits._
import java.security.MessageDigest

import uk.gov.nationalarchives.consignmentexport.ChecksumCalculator.ChecksumFile

class ChecksumCalculator {

  def calculateChecksums(files: File*): IO[List[ChecksumFile]] = {
    files.map(generate).toList.sequence
  }

  private def generate(file: File): IO[ChecksumFile] = {
    val chunkSizeInBytes: Int = 100 * 1024 * 1024
    val messageDigester: MessageDigest = MessageDigest.getInstance("SHA-256")

    for {
      _ <- {
        Resource.fromAutoCloseable(IO(new FileInputStream(file)))
          .use(inStream => {
            val bytes = new Array[Byte](chunkSizeInBytes)
            IO(Iterator.continually(inStream.read(bytes)).takeWhile(_ != -1).foreach(messageDigester.update(bytes, 0, _)))
          })
      }
      checksum <- IO(messageDigester.digest)
      mapped <- IO(checksum.map(byte => f"$byte%02x").mkString)
    } yield ChecksumFile(file, mapped)
  }
}

object ChecksumCalculator {
  case class ChecksumFile(file: File, checksum: String)

  def apply(): ChecksumCalculator = new ChecksumCalculator()
}

package uk.gov.nationalarchives.consignmentexport

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Path, Paths}
import java.util
import cats.effect.IO

import gov.loc.repository.bagit.domain.{Bag, Manifest, Metadata, Version}
import gov.loc.repository.bagit.hash.{StandardSupportedAlgorithms, SupportedAlgorithm}
import org.mockito.ArgumentCaptor
import uk.gov.nationalarchives.consignmentexport.ChecksumCalculator.ChecksumFile
import cats.effect.unsafe.implicits.global
import scala.jdk.CollectionConverters._

class BagitSpec extends ExportSpec {
  "the createBag method" should "call the bagit methods with the correct values" in {
    val createMock = mock[(Path, util.Collection[SupportedAlgorithm], Boolean, Metadata) => Bag]
    val verifyMock = mock[(Bag, Boolean) => IO[Unit]]
    val metadataMock = mock[Metadata]

    val createPath: ArgumentCaptor[Path] = ArgumentCaptor.forClass(classOf[Path])
    val createAlgorithms: ArgumentCaptor[util.Collection[SupportedAlgorithm]] = ArgumentCaptor.forClass(classOf[util.Collection[SupportedAlgorithm]])
    val createIncludeHidden: ArgumentCaptor[Boolean] = ArgumentCaptor.forClass(classOf[Boolean])
    val createMetadata: ArgumentCaptor[Metadata] = ArgumentCaptor.forClass(classOf[Metadata])
    val verifyBag: ArgumentCaptor[Bag] = ArgumentCaptor.forClass(classOf[Bag])
    val verifyIncludeHidden: ArgumentCaptor[Boolean] = ArgumentCaptor.forClass(classOf[Boolean])

    val bag = new Bag(Version.LATEST_BAGIT_VERSION())
    val bagit = new Bagit(createMock, verifyMock, mock[(util.Set[Manifest], Path, Path, Charset) => Unit])
    val consignmentReference = "Consignment-Reference"

    doAnswer(() => bag).when(createMock).apply(createPath.capture(), createAlgorithms.capture(), createIncludeHidden.capture(), createMetadata.capture())
    doAnswer(() => IO()).when(verifyMock).apply(verifyBag.capture(), verifyIncludeHidden.capture())

    bagit.createBag(consignmentReference, "root", metadataMock).unsafeRunSync()

    createPath.getValue.toString should equal(s"root/$consignmentReference")
    createAlgorithms.getValue.toArray()(0) should equal(StandardSupportedAlgorithms.SHA256)
    createIncludeHidden.getValue should equal(true)
    createMetadata.getValue should equal(metadataMock)

    verifyBag.getValue.getVersion should equal(Version.LATEST_BAGIT_VERSION())
    verifyIncludeHidden.getValue should equal(true)
  }

  "the writeMetadataFilesToBag method" should "call the bagit method with the correct values" in {
    val writeTagManifests = mock[(util.Set[Manifest], Path, Path, Charset) => Unit]

    val outputPath: ArgumentCaptor[Path] = ArgumentCaptor.forClass(classOf[Path])
    val bagitRootPath: ArgumentCaptor[Path] = ArgumentCaptor.forClass(classOf[Path])
    val charset: ArgumentCaptor[Charset] = ArgumentCaptor.forClass(classOf[Charset])
    val manifests: ArgumentCaptor[util.Set[Manifest]] = ArgumentCaptor.forClass(classOf[util.Set[Manifest]])

    val bagit = new Bagit(mock[(Path, util.Collection[SupportedAlgorithm], Boolean, Metadata) => Bag], mock[(Bag, Boolean) => IO[Unit]], writeTagManifests)

    val bag = new Bag()
    bag.setFileEncoding(Charset.defaultCharset())
    bag.setRootDir(Paths.get("rootPath"))
    bag.setTagManifests(Set(new Manifest(StandardSupportedAlgorithms.SHA256)).asJava)

    when(writeTagManifests.apply(manifests.capture(), outputPath.capture(), bagitRootPath.capture(), charset.capture())).thenReturn(())

    bagit.writeTagManifestRows(bag, List(ChecksumFile(new File("path"), "checksum"))).unsafeRunSync()

    val fileToChecksumMap = manifests.getValue.asScala.head.getFileToChecksumMap

    fileToChecksumMap.get(Paths.get("path")) should equal("checksum")
    outputPath.getValue.toString should equal("rootPath")
    bagitRootPath.getValue.toString should equal("rootPath")
    charset.getValue should equal(Charset.defaultCharset())
  }
}

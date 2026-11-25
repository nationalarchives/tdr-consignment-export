package uk.gov.nationalarchives.`export`

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger

class MetadataUtilsSpec extends AnyFlatSpec with MockitoSugar {
  "Database connections" should "generate a fresh token for each acquisition and close each connection" in {
    val tokensGenerated = new AtomicInteger()
    val firstConnection = mock[Connection]
    val secondConnection = mock[Connection]
    val jdbcUrl = "jdbc:postgresql://localhost:5432/consignmentapi"
    val openConnection = mock[(String, String, String) => Connection]
    when(openConnection(jdbcUrl, "tdr", "token-1")).thenReturn(firstConnection)
    when(openConnection(jdbcUrl, "tdr", "token-2")).thenReturn(secondConnection)

    val transactor = MetadataUtils.createTransactor(
      jdbcUrl,
      "tdr",
      () => s"token-${tokensGenerated.incrementAndGet()}",
      openConnection
    )
    val acquire = transactor.connect(())

    tokensGenerated.get() shouldEqual 0
    acquire.use(connection => IO(connection shouldEqual firstConnection)).unsafeRunSync()
    acquire.use(connection => IO(connection shouldEqual secondConnection)).unsafeRunSync()

    tokensGenerated.get() shouldEqual 2
    verify(openConnection).apply(jdbcUrl, "tdr", "token-1")
    verify(openConnection).apply(jdbcUrl, "tdr", "token-2")
    verify(firstConnection).close()
    verify(secondConnection).close()
  }

  "MetadataPropertyNames" should "return the correct id value" in {
    MetadataUtils.AssetId.id shouldEqual "AssetId"
    MetadataUtils.ClientSideOriginalFilepath.id shouldEqual "ClientSideOriginalFilepath"
    MetadataUtils.ConsignmentId.id shouldEqual "ConsignmentId"
    MetadataUtils.ConsignmentReference.id shouldEqual "ConsignmentReference"
    MetadataUtils.FileReference.id shouldEqual "FileReference"
    MetadataUtils.MetadataSchemaLibraryVersion.id shouldEqual "MetadataSchemaLibraryVersion"
    MetadataUtils.OriginalFilepath.id shouldEqual "OriginalFilepath"
    MetadataUtils.OriginalFileReference.id shouldEqual "OriginalFileReference"
    MetadataUtils.Series.id shouldEqual "Series"
    MetadataUtils.TransferInitiatedDatetime.id shouldEqual "TransferInitiatedDatetime"
    MetadataUtils.TransferringBody.id shouldEqual "TransferringBody"
    MetadataUtils.UserId.id shouldEqual "UserId"
  }
}

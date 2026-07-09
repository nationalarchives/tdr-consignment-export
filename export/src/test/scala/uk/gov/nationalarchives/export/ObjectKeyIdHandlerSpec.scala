package uk.gov.nationalarchives.`export`

import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.not
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import uk.gov.nationalarchives.`export`.MetadataUtils.Metadata

import java.util.UUID

class ObjectKeyIdHandlerSpec extends AnyFlatSpec with MockitoSugar {
  private val fileOneId = UUID.randomUUID()
  private val fileOneAssetId = UUID.randomUUID()
  private val fileTwoId = UUID.randomUUID()
  private val fileTwoAssetId = UUID.randomUUID()

  "getObjectKeyIds" should "return the asset and file ids for each record where the asset id is persisted" in {
    val fileOneMetadata = List(
      Metadata(fileOneId, "ClientSideOriginalFilepath", "a/file/path"),
      Metadata(fileOneId, "AssetId", fileOneAssetId.toString)
    )

    val fileTwoMetadata = List(
      Metadata(fileTwoId, "ClientSideOriginalFilepath", "b/file/path"),
      Metadata(fileTwoId, "AssetId", fileTwoAssetId.toString)
    )

    val result = ObjectKeyIdHandler.getObjectKeyIds(List(fileTwoMetadata, fileOneMetadata).flatten)
    result.size shouldBe 2

    val fileOneIds = result(fileOneId)
    fileOneIds.assetId shouldEqual fileOneAssetId
    fileOneIds.digitalObjectKeyId shouldEqual fileOneId

    val fileTwoIds = result(fileTwoId)
    fileTwoIds.assetId shouldEqual fileTwoAssetId
    fileTwoIds.digitalObjectKeyId shouldEqual fileTwoId
  }

  "getObjectKeyIds" should "return the file id as the asset id and generate a new digital object key id where asset id is not persisted" in {
    val fileWithoutAssetIdMetadata = List(
      Metadata(fileOneId, "ClientSideOriginalFilepath", "a/file/path")
    )

    val fileTwoMetadata = List(
      Metadata(fileTwoId, "ClientSideOriginalFilepath", "b/file/path"),
      Metadata(fileTwoId, "AssetId", fileTwoAssetId.toString)
    )

    val result = ObjectKeyIdHandler.getObjectKeyIds(List(fileTwoMetadata, fileWithoutAssetIdMetadata).flatten)
    result.size shouldBe 2

    val fileWithoutAssetId = result(fileOneId)
    fileWithoutAssetId.assetId shouldEqual fileOneId
    fileWithoutAssetId.digitalObjectKeyId should not equal fileOneId

    val fileTwoIds = result(fileTwoId)
    fileTwoIds.assetId shouldEqual fileTwoAssetId
    fileTwoIds.digitalObjectKeyId shouldEqual fileTwoId
  }
}

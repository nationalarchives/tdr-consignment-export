package uk.gov.nationalarchives.`export`

import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class MetadataUtilsSpec extends AnyFlatSpec with MockitoSugar {
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

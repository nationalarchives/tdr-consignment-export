package uk.gov.nationalarchives.`export`

import java.util.UUID

object RecordIdHandler {
  private val assetIdName = "AssetId"
  case class RecordIds(assetId: UUID, fileId: UUID)

  def getRecordIds(fileMetadata: List[MetadataUtils.Metadata]): Map[UUID, RecordIds] = {
    val groupedMetadata = fileMetadata.groupBy(fm => fm.id)
    groupedMetadata.map(gm => {
      val fileId = gm._1
      val fileMetadata = gm._2
      val noPersistedAssetId = !fileMetadata.exists(_.propertyName == assetIdName)

      //Keep current behaviour until all legacy consignments without asset id persisted have been processed
      //Current behaviour use fileId as assetId and generate random UUID for the fileId
      if (noPersistedAssetId) {
        fileId -> RecordIds(fileId, UUID.randomUUID())
      } else {
        val persistedAssetId = fileMetadata.find(_.propertyName == assetIdName).get.value
        fileId -> RecordIds(UUID.fromString(persistedAssetId), fileId)
      }
    })
  }
}

package uk.gov.nationalarchives.`export`

import java.util.UUID
import uk.gov.nationalarchives.`export`.MetadataUtils.{Metadata, AssetId}

object RecordIdHandler {
  case class RecordIds(assetId: UUID, fileId: UUID)

  def getRecordIds(fileMetadata: List[Metadata]): Map[UUID, RecordIds] = {
    val groupedMetadata = fileMetadata.groupBy(fm => fm.id)
    groupedMetadata.map(gm => {
      val fileId = gm._1
      val metadata = gm._2
      val noPersistedAssetId = !metadata.exists(_.propertyName == AssetId.id)

      //Keep current behaviour until all legacy consignments without asset ids persisted have been processed
      //Current behaviour use fileId as assetId and generate random UUID for the fileId
      if (noPersistedAssetId) {
        fileId -> RecordIds(fileId, UUID.randomUUID())
      } else {
        val persistedAssetId = metadata.find(_.propertyName == AssetId.id).get.value
        fileId -> RecordIds(UUID.fromString(persistedAssetId), fileId)
      }
    })
  }
}

package uk.gov.nationalarchives.`export`

import java.util.UUID
import uk.gov.nationalarchives.`export`.MetadataUtils.{Metadata, AssetId}

object ObjectKeyIdHandler {
  case class ObjectKeyIds(assetId: UUID, digitalObjectKeyId: UUID, tdrFileId: UUID)

  def getObjectKeyIds(fileMetadata: List[Metadata]): Map[UUID, ObjectKeyIds] = {
    val groupedMetadata = fileMetadata.groupBy(fm => fm.id)
    groupedMetadata.map(gm => {
      val tdrFileId = gm._1
      val metadata = gm._2
      val noPersistedAssetId = !metadata.exists(_.propertyName == AssetId.id)

      //Keep current behaviour until all legacy consignments without asset ids persisted have been processed
      //Current behaviour use fileId as assetId and generate random UUID for the file object key id
      val objectKeyIds = if (noPersistedAssetId) { ObjectKeyIds(tdrFileId, UUID.randomUUID(), tdrFileId) } else {
        val persistedAssetId = metadata.find(_.propertyName == AssetId.id).get.value
        ObjectKeyIds(UUID.fromString(persistedAssetId), tdrFileId, tdrFileId)
      }

      tdrFileId -> objectKeyIds
    })
  }
}

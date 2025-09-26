package uk.gov.nationalarchives.consignmentexport

import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import uk.gov.nationalarchives.consignmentexport.Config.ConsignmentTypeOverride

object Overrides {
  def consignmentTypeMessageOverride(originalConsignmentType: ConsignmentType, consignmentData: GetConsignment, consignmentTypeOverride: ConsignmentTypeOverride): String = {
    originalConsignmentType match {
      case Standard if
        historicalTribunalTransfer(consignmentTypeOverride, consignmentData) => "historicalTribunal"
      case _ => originalConsignmentType.name
    }
  }

  private def historicalTribunalTransfer(consignmentTypeOverride: ConsignmentTypeOverride, consignmentData: GetConsignment): Boolean = {
    consignmentTypeOverride.transferringBodies.exists(consignmentData.transferringBodyName.contains) &&
      consignmentTypeOverride.judgmentSeries.exists(consignmentData.seriesName.contains)
  }
}

package uk.gov.nationalarchives.consignmentexport

import graphql.codegen.GetConsignmentExport.getConsignmentForExport.GetConsignment
import uk.gov.nationalarchives.consignmentexport.Config.ConsignmentTypeOverride

object Overrides {
  def consignmentTypeMessageOverride(originalConsignmentType: String, consignmentData: GetConsignment, consignmentTypeOverride: ConsignmentTypeOverride): String = {
    originalConsignmentType.toLowerCase match {
      case "standard" if
        historicalTribunalTransfer(consignmentTypeOverride, consignmentData) => "historicalTribunal"
      case _ => originalConsignmentType
    }
  }

  private def historicalTribunalTransfer(consignmentTypeOverride: ConsignmentTypeOverride, consignmentData: GetConsignment): Boolean = {
    consignmentTypeOverride.transferringBodies.contains(consignmentData.transferringBodyName.get) &&
      consignmentTypeOverride.judgmentSeries.contains(consignmentData.seriesName.get)
  }
}

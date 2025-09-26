package uk.gov.nationalarchives.consignmentexport

object ConsignmentStatus {
  object StatusType {
    val export = "Export"
  }

  object StatusValue {
    val completed = "Completed"
    val failed = "Failed"
  }
}

sealed trait ConsignmentType { val name: String }
case object Standard extends ConsignmentType { val name = "standard" }
case object Judgment extends ConsignmentType { val name = "judgment" }

object ConsignmentType {
  def apply(name: String): ConsignmentType = name match {
    case Standard.name => Standard
    case Judgment.name => Judgment
  }
}

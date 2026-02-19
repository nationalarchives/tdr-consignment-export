package uk.gov.nationalarchives.`export`

import cats.implicits.catsSyntaxTuple4Semigroupal
import com.monovore.decline.Opts

import java.util.UUID

object Arguments {
  private def toBoolean(booleanString: String): Boolean = {
    try {
      booleanString.toBoolean
    } catch {
      case _: Throwable => false
    }
  }

  case class FileExport(consignmentId: UUID, taskToken: String, rerunExportOnly: Boolean = false, rerunBagitOnly: Boolean = false)

  val consignmentId: Opts[UUID] = Opts.option[UUID]("consignmentId", "The id for the consignment")
  val taskToken: Opts[String] = Opts.option[String]("taskToken", "The task token passed to ECS task from the step function")
  val rerunBagitOnly: Opts[String] = Opts.option[String]("rerunBagitOnly", "Whether a bagit rerun").withDefault("false")
  val rerunExportOnly: Opts[String] = Opts.option[String]("rerunExportOnly", "Whether an export rerun").withDefault("false")

  val exportOpts: Opts[FileExport] =
    Opts.subcommand("export", "Exports files") {
      (consignmentId, taskToken, rerunExportOnly, rerunBagitOnly) mapN { (ci, tt, rre, rrb) =>
        FileExport(ci, tt, toBoolean(rre), toBoolean(rrb))
      }
    }
}

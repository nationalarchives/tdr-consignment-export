package uk.gov.nationalarchives.consignmentexport

import cats.implicits.catsSyntaxTuple4Semigroupal
import com.monovore.decline.Opts

import java.util.UUID

object Arguments {
  case class FileExport(consignmentId: UUID, taskToken: String, exportRerun: Boolean, bagitRerun: Boolean)

  val consignmentId: Opts[UUID] = Opts.option[UUID]("consignmentId", "The id for the consignment")
  val taskToken: Opts[String] = Opts.option[String]("taskToken", "The task token passed to ECS task from the step function")
  val exportRerun: Opts[Boolean] = Opts.flag("exportRerun", "Whether an export rerun").orFalse
  val bagitRerun = Opts.flag("bagitRerun", "Whether a bagit rerun").orFalse

  val exportOps: Opts[FileExport] =
    Opts.subcommand("export", "Creates a bagit package") {
      (consignmentId, taskToken, exportRerun, bagitRerun) mapN {
        (ci, tt, exprr, brr) => FileExport(ci, tt, exprr, brr)
      }
    }
}

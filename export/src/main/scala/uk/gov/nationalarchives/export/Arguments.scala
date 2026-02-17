package uk.gov.nationalarchives.`export`

import cats.implicits.catsSyntaxTuple3Semigroupal
import com.monovore.decline.Opts

import java.util.UUID

object Arguments {
  case class FileExport(consignmentId: UUID, taskToken: String, bagitRerun: Boolean)

  val consignmentId: Opts[UUID] = Opts.option[UUID]("consignmentId", "The id for the consignment")
  val taskToken: Opts[String] = Opts.option[String]("taskToken", "The task token passed to ECS task from the step function")
  val bagitRerun = Opts.flag("bagitRerun", "Whether a bagit rerun").orFalse

  val exportOpts: Opts[FileExport] =
    Opts.subcommand("export", "Exports files") {
      (consignmentId, taskToken, bagitRerun) mapN { (ci, tt, brr) =>
        FileExport(ci, tt, brr)
      }
    }
}

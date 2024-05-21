package uk.gov.nationalarchives.consignmentexport

import java.util.UUID

import cats.implicits.catsSyntaxTuple2Semigroupal
import com.monovore.decline.Opts

object Arguments {
  case class FileExport(consignmentId: UUID, taskToken: String)

  val consignmentId: Opts[UUID] = Opts.option[UUID]("consignmentId", "The id for the consignment")
  val taskToken: Opts[String] = Opts.option[String]("taskToken", "The task token passed to ECS task from the step function")

  val exportOps: Opts[FileExport] =
    Opts.subcommand("export", "Creates a bagit package") {
      (consignmentId, taskToken) mapN {
        (ci, tt) => FileExport(ci, tt)
      }
    }
}

package uk.gov.nationalarchives

import cats.implicits.catsSyntaxTuple2Semigroupal
import com.monovore.decline.Opts

import java.util.UUID

object Arguments {
  case class FileExport(consignmentId: UUID, taskToken: String)

  val consignmentId: Opts[UUID] = Opts.option[UUID]("consignmentId", "The id for the consignment")
  val taskToken: Opts[String] = Opts.option[String]("taskToken", "The task token passed to ECS task from the step function")

  val exportOpts: Opts[FileExport] =
    Opts.subcommand("export", "Exports files") {
      (consignmentId, taskToken) mapN {
        (ci, tt) => FileExport(ci, tt)
      }
    }
}

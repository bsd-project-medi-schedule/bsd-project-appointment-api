package utils

import cats.effect.{IO, IOApp}
import java.nio.file.{Files, Paths}

object GenerateTestExcel extends IOApp.Simple {

  override def run: IO[Unit] =
    for {
      bytes <- ExcelService.generateSampleExcel()
      _ <- IO(Files.write(Paths.get("doctors_import_template.xlsx"), bytes))
      _ <- IO.println("Generated test Excel file: doctors_import_template.xlsx")
    } yield ()
}
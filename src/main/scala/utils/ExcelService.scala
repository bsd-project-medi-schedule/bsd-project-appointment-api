package utils

import cats.effect.IO
import org.apache.poi.ss.usermodel.{Cell, CellType, Row, WorkbookFactory}
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import DTO.DoctorImportDTO
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import scala.jdk.CollectionConverters._

object ExcelService {

  def parseDoctorsFromExcel(inputStream: InputStream): IO[List[DoctorImportDTO]] = IO {
    val workbook = WorkbookFactory.create(inputStream)
    try {
      val sheet = workbook.getSheetAt(0)
      val rows = sheet.iterator().asScala.toList.drop(1)

      rows.flatMap { row =>
        try {
          val fieldOfAction = getCellValue(row, 0)
          val firstName = getCellValue(row, 1)
          val lastName = getCellValue(row, 2)
          val email = getCellValue(row, 3)
          val phone = Option(getCellValue(row, 4)).filter(_.nonEmpty)
          val password = getCellValue(row, 5)

          if (fieldOfAction.nonEmpty && firstName.nonEmpty && lastName.nonEmpty &&
              email.nonEmpty && password.nonEmpty) {
            Some(DoctorImportDTO(
              fieldOfAction = fieldOfAction,
              firstName = firstName,
              lastName = lastName,
              email = email,
              phone = phone,
              password = password
            ))
          } else None
        } catch {
          case _: Exception => None
        }
      }
    } finally {
      workbook.close()
      inputStream.close()
    }
  }

  private def getCellValue(row: Row, cellIndex: Int): String = {
    Option(row.getCell(cellIndex)).map { cell =>
      cell.getCellType match {
        case CellType.STRING => cell.getStringCellValue.trim
        case CellType.NUMERIC => cell.getNumericCellValue.toString.trim
        case CellType.BOOLEAN => cell.getBooleanCellValue.toString
        case _ => ""
      }
    }.getOrElse("")
  }

  def generateSampleExcel(): IO[Array[Byte]] = IO {
    val workbook = new XSSFWorkbook()
    try {
      val sheet = workbook.createSheet("Doctors")

      val headerRow = sheet.createRow(0)
      val headers = List("Field of Action", "First Name", "Last Name", "Email", "Phone", "Password")
      headers.zipWithIndex.foreach { case (header, idx) =>
        val cell = headerRow.createCell(idx)
        cell.setCellValue(header)
      }

      val sampleData = List(
        ("Surgeon", "John", "Smith", "john.smith@example.com", "+1234567890", "Password123!"),
        ("Cardiologist", "Jane", "Doe", "jane.doe@example.com", "+0987654321", "SecurePass456!"),
        ("Neurologist", "Robert", "Johnson", "robert.j@example.com", "", "StrongPwd789!"),
        ("Pediatrician", "Emily", "Williams", "emily.w@example.com", "+1122334455", "ChildCare2024!"),
        ("Dermatologist", "Michael", "Brown", "michael.b@example.com", "+5566778899", "SkinHealth123!")
      )

      sampleData.zipWithIndex.foreach { case ((field, first, last, email, phone, password), idx) =>
        val row = sheet.createRow(idx + 1)
        row.createCell(0).setCellValue(field)
        row.createCell(1).setCellValue(first)
        row.createCell(2).setCellValue(last)
        row.createCell(3).setCellValue(email)
        row.createCell(4).setCellValue(phone)
        row.createCell(5).setCellValue(password)
      }

      headers.indices.foreach(sheet.autoSizeColumn)

      val outputStream = new ByteArrayOutputStream()
      workbook.write(outputStream)
      outputStream.toByteArray
    } finally {
      workbook.close()
    }
  }
}
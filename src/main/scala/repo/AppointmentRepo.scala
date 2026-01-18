package repo

import DTO.AppointmentDTO
import cats.effect.IO
import java.time.Instant
import java.util.UUID

trait AppointmentRepo {
  def createAndGetId(appointment: AppointmentDTO): IO[Option[UUID]]
  def readById(id: UUID): IO[Option[AppointmentDTO]]
  def readByPatient(patientId: UUID, offset: Int, size: Int): IO[List[AppointmentDTO]]
  def readByDoctor(doctorId: UUID, offset: Int, size: Int): IO[List[AppointmentDTO]]
  def readByDoctorAndDate(doctorId: UUID, date: Instant): IO[List[AppointmentDTO]]
  def readByOffice(officeId: UUID, offset: Int, size: Int): IO[List[AppointmentDTO]]
  def readByConfirmationToken(token: String): IO[Option[AppointmentDTO]]
  def readPendingReminders(beforeTime: Instant): IO[List[AppointmentDTO]]
  def update(id: UUID, appointment: AppointmentDTO): IO[Int]
  def updateStatus(id: UUID, status: String): IO[Int]
  def confirmAppointment(id: UUID): IO[Int]
  def markReminderSent(id: UUID): IO[Int]
  def delete(id: UUID): IO[Int]
  def hasConflict(doctorId: UUID, startTime: Instant, endTime: Instant, excludeId: Option[UUID] = None): IO[Boolean]
}
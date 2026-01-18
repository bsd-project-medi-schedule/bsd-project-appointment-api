package repo

import DTO.ScheduleDTO
import cats.effect.IO
import java.util.UUID

trait ScheduleRepo {
  def createAndGetId(schedule: ScheduleDTO): IO[Option[UUID]]
  def readById(id: UUID): IO[Option[ScheduleDTO]]
  def readByDoctor(doctorId: UUID): IO[List[ScheduleDTO]]
  def readByDoctorAndDay(doctorId: UUID, dayOfWeek: Int): IO[Option[ScheduleDTO]]
  def update(id: UUID, schedule: ScheduleDTO): IO[Int]
  def delete(id: UUID): IO[Int]
  def deleteByDoctor(doctorId: UUID): IO[Int]
}
package impl

import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import java.time.LocalTime
import java.util.UUID
import repo.ScheduleRepo
import DTO.ScheduleDTO

final case class ScheduleRepoImpl()(implicit t: Transactor[IO]) extends ScheduleRepo {

  implicit val localTimeGet: Get[LocalTime] =
    Get[java.sql.Time].map(_.toLocalTime)

  implicit val localTimePut: Put[LocalTime] =
    Put[java.sql.Time].contramap(java.sql.Time.valueOf)

  override def createAndGetId(schedule: ScheduleDTO): IO[Option[UUID]] =
    sql"""
      INSERT INTO doctor_schedule (doctor_id, day_of_week, start_time, end_time, slot_duration_min, is_active)
      VALUES (${schedule.doctorId}, ${schedule.dayOfWeek}, ${schedule.startTime},
              ${schedule.endTime}, ${schedule.slotDurationMin}, ${schedule.isActive.getOrElse(true)})
      RETURNING id
    """.query[UUID].option.transact(t)

  override def readById(id: UUID): IO[Option[ScheduleDTO]] =
    sql"""
      SELECT id, doctor_id, day_of_week, start_time, end_time, slot_duration_min, is_active
      FROM doctor_schedule
      WHERE id = $id
    """.query[(UUID, UUID, Int, LocalTime, LocalTime, Int, Boolean)].option.map(_.map {
      case (id, doctorId, dayOfWeek, startTime, endTime, slotDurationMin, isActive) =>
        ScheduleDTO(Some(id), doctorId, dayOfWeek, startTime, endTime, slotDurationMin, Some(isActive))
    }).transact(t)

  override def readByDoctor(doctorId: UUID): IO[List[ScheduleDTO]] =
    sql"""
      SELECT id, doctor_id, day_of_week, start_time, end_time, slot_duration_min, is_active
      FROM doctor_schedule
      WHERE doctor_id = $doctorId AND is_active = true
      ORDER BY day_of_week
    """.query[(UUID, UUID, Int, LocalTime, LocalTime, Int, Boolean)].to[List].map(_.map {
      case (id, doctorId, dayOfWeek, startTime, endTime, slotDurationMin, isActive) =>
        ScheduleDTO(Some(id), doctorId, dayOfWeek, startTime, endTime, slotDurationMin, Some(isActive))
    }).transact(t)

  override def readByDoctorAndDay(doctorId: UUID, dayOfWeek: Int): IO[Option[ScheduleDTO]] =
    sql"""
      SELECT id, doctor_id, day_of_week, start_time, end_time, slot_duration_min, is_active
      FROM doctor_schedule
      WHERE doctor_id = $doctorId AND day_of_week = $dayOfWeek AND is_active = true
    """.query[(UUID, UUID, Int, LocalTime, LocalTime, Int, Boolean)].option.map(_.map {
      case (id, doctorId, dayOfWeek, startTime, endTime, slotDurationMin, isActive) =>
        ScheduleDTO(Some(id), doctorId, dayOfWeek, startTime, endTime, slotDurationMin, Some(isActive))
    }).transact(t)

  override def update(id: UUID, schedule: ScheduleDTO): IO[Int] =
    sql"""
      UPDATE doctor_schedule
      SET day_of_week = ${schedule.dayOfWeek},
          start_time = ${schedule.startTime},
          end_time = ${schedule.endTime},
          slot_duration_min = ${schedule.slotDurationMin},
          is_active = ${schedule.isActive.getOrElse(true)}
      WHERE id = $id
    """.update.run.transact(t)

  override def delete(id: UUID): IO[Int] =
    sql"UPDATE doctor_schedule SET is_active = false WHERE id = $id".update.run.transact(t)

  override def deleteByDoctor(doctorId: UUID): IO[Int] =
    sql"UPDATE doctor_schedule SET is_active = false WHERE doctor_id = $doctorId".update.run.transact(t)
}
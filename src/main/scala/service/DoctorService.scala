package service

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.either._
import java.util.UUID
import repo.{DoctorRepo, ScheduleRepo}
import DTO.{DoctorDTO, ScheduleDTO}
import error.ServiceError

final case class DoctorService(doctorRepo: DoctorRepo, scheduleRepo: ScheduleRepo) {

  def createDoctor(doctor: DoctorDTO): EitherT[IO, ServiceError, UUID] = {
    for {
      _ <- EitherT.cond[IO](
        doctor.firstName.nonEmpty,
        (),
        ServiceError.BadRequest("First name is required"): ServiceError
      )
      _ <- EitherT.cond[IO](
        doctor.lastName.nonEmpty,
        (),
        ServiceError.BadRequest("Last name is required"): ServiceError
      )
      _ <- EitherT.cond[IO](
        doctor.email.nonEmpty,
        (),
        ServiceError.BadRequest("Email is required"): ServiceError
      )
      _ <- EitherT.cond[IO](
        doctor.fieldOfAction.nonEmpty,
        (),
        ServiceError.BadRequest("Field of action is required"): ServiceError
      )
      doctorId <- EitherT.fromOptionF(
        doctorRepo.createAndGetId(doctor),
        ServiceError.InternalError("Could not create doctor"): ServiceError
      )
    } yield doctorId
  }

  def readDoctor(id: UUID): EitherT[IO, ServiceError, DoctorDTO] =
    EitherT.fromOptionF(doctorRepo.readById(id), ServiceError.NotFound("Doctor not found"): ServiceError)

  def readDoctorByUserId(userId: UUID): EitherT[IO, ServiceError, DoctorDTO] =
    EitherT.fromOptionF(doctorRepo.readByUserId(userId), ServiceError.NotFound("Doctor not found"): ServiceError)

  def readDoctorsByOffice(officeId: UUID, offset: Int, size: Int): EitherT[IO, ServiceError, List[DoctorDTO]] =
    EitherT(doctorRepo.readByOffice(officeId, offset, size).map(_.asRight[ServiceError]))

  def readDoctors(offset: Int, size: Int): EitherT[IO, ServiceError, List[DoctorDTO]] =
    EitherT(doctorRepo.readAll(offset, size).map(_.asRight[ServiceError]))

  def updateDoctor(id: UUID, doctor: DoctorDTO): EitherT[IO, ServiceError, Unit] =
    EitherT(doctorRepo.update(id, doctor).map {
      case 0 => Left(ServiceError.NotFound("Doctor not found"))
      case _ => Right(())
    })

  def deleteDoctor(id: UUID): EitherT[IO, ServiceError, Unit] =
    EitherT(doctorRepo.delete(id).map {
      case 0 => Left(ServiceError.NotFound("Doctor not found"))
      case _ => Right(())
    })

  def addServiceToDoctor(doctorId: UUID, serviceId: UUID): EitherT[IO, ServiceError, Unit] =
    EitherT(doctorRepo.addServiceToDoctor(doctorId, serviceId).map(_ => Right(()): Either[ServiceError, Unit]))

  def removeServiceFromDoctor(doctorId: UUID, serviceId: UUID): EitherT[IO, ServiceError, Unit] =
    EitherT(doctorRepo.removeServiceFromDoctor(doctorId, serviceId).map(_ => Right(()): Either[ServiceError, Unit]))

  def getDoctorServices(doctorId: UUID): EitherT[IO, ServiceError, List[UUID]] =
    EitherT(doctorRepo.getDoctorServices(doctorId).map(_.asRight[ServiceError]))

  def getDoctorsForService(serviceId: UUID, officeId: UUID): EitherT[IO, ServiceError, List[DoctorDTO]] =
    EitherT(doctorRepo.getDoctorsForService(serviceId, officeId).map(_.asRight[ServiceError]))

  def createSchedule(schedule: ScheduleDTO): EitherT[IO, ServiceError, UUID] = {
    for {
      _ <- EitherT.cond[IO](
        schedule.dayOfWeek >= 0 && schedule.dayOfWeek <= 6,
        (),
        ServiceError.BadRequest("Invalid day of week (must be 0-6)"): ServiceError
      )
      _ <- EitherT.cond[IO](
        schedule.startTime.isBefore(schedule.endTime),
        (),
        ServiceError.BadRequest("Start time must be before end time"): ServiceError
      )
      scheduleId <- EitherT.fromOptionF(
        scheduleRepo.createAndGetId(schedule),
        ServiceError.InternalError("Could not create schedule"): ServiceError
      )
    } yield scheduleId
  }

  def getSchedule(doctorId: UUID): EitherT[IO, ServiceError, List[ScheduleDTO]] =
    EitherT(scheduleRepo.readByDoctor(doctorId).map(_.asRight[ServiceError]))

  def getScheduleForDay(doctorId: UUID, dayOfWeek: Int): EitherT[IO, ServiceError, ScheduleDTO] =
    EitherT.fromOptionF(
      scheduleRepo.readByDoctorAndDay(doctorId, dayOfWeek),
      ServiceError.NotFound("Schedule not found for this day"): ServiceError
    )

  def updateSchedule(id: UUID, schedule: ScheduleDTO): EitherT[IO, ServiceError, Unit] =
    EitherT(scheduleRepo.update(id, schedule).map {
      case 0 => Left(ServiceError.NotFound("Schedule not found"))
      case _ => Right(())
    })

  def deleteSchedule(id: UUID): EitherT[IO, ServiceError, Unit] =
    EitherT(scheduleRepo.delete(id).map {
      case 0 => Left(ServiceError.NotFound("Schedule not found"))
      case _ => Right(())
    })
}
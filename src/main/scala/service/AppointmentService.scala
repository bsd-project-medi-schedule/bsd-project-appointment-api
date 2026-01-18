package service

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.either._
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.util.UUID
import repo.{AppointmentRepo, DoctorRepo, ScheduleRepo, ServiceRepo}
import DTO.{AppointmentDTO, BookAppointmentDTO, AppointmentSlotDTO}
import error.ServiceError
import utils.NonceService
import utils.Sha256Service

final case class AppointmentService(
  appointmentRepo: AppointmentRepo,
  doctorRepo: DoctorRepo,
  scheduleRepo: ScheduleRepo,
  serviceRepo: ServiceRepo
) {

  def bookAppointment(patientId: UUID, booking: BookAppointmentDTO): EitherT[IO, ServiceError, (UUID, String)] = {
    for {
      service <- EitherT.fromOptionF(
        serviceRepo.readById(booking.serviceId),
        ServiceError.NotFound("Service not found"): ServiceError
      )

      doctors <- EitherT.liftF(doctorRepo.getDoctorsForService(booking.serviceId, booking.officeId))
      _ <- EitherT.cond[IO](
        doctors.nonEmpty,
        (),
        ServiceError.BadRequest("No doctors available for this service"): ServiceError
      )

      appointmentTime = booking.appointmentTime
      endTime = appointmentTime.plusSeconds(service.durationInMinutes * 60)
      dayOfWeek = ZonedDateTime.ofInstant(appointmentTime, ZoneId.systemDefault()).getDayOfWeek.getValue % 7

      availableDoctor <- findAvailableDoctor(doctors.map(_.id.get), dayOfWeek, appointmentTime, endTime)

      confirmationToken = NonceService.generateNonce()
      tokenHash = Sha256Service.hash(confirmationToken)

      appointment = AppointmentDTO(
        patientId = patientId,
        doctorId = availableDoctor,
        officeId = booking.officeId,
        serviceId = booking.serviceId,
        appointmentTime = appointmentTime,
        endTime = Some(endTime),
        status = "PENDING",
        notes = booking.notes,
        confirmationToken = Some(tokenHash)
      )

      appointmentId <- EitherT.fromOptionF(
        appointmentRepo.createAndGetId(appointment),
        ServiceError.InternalError("Could not create appointment"): ServiceError
      )
    } yield (appointmentId, confirmationToken)
  }

  private def findAvailableDoctor(
    doctorIds: List[UUID],
    dayOfWeek: Int,
    startTime: Instant,
    endTime: Instant
  ): EitherT[IO, ServiceError, UUID] = {
    def checkDoctor(id: UUID): IO[Option[UUID]] = {
      for {
        schedule <- scheduleRepo.readByDoctorAndDay(id, dayOfWeek)
        hasConflict <- appointmentRepo.hasConflict(id, startTime, endTime)
        localTime = ZonedDateTime.ofInstant(startTime, ZoneId.systemDefault()).toLocalTime
        localEndTime = ZonedDateTime.ofInstant(endTime, ZoneId.systemDefault()).toLocalTime
      } yield {
        schedule match {
          case Some(s) if !hasConflict &&
            !localTime.isBefore(s.startTime) &&
            !localEndTime.isAfter(s.endTime) => Some(id)
          case _ => None
        }
      }
    }

    EitherT(
      doctorIds.foldLeft(IO.pure(Option.empty[UUID])) { (accIO, doctorId) =>
        accIO.flatMap {
          case Some(found) => IO.pure(Some(found))
          case None => checkDoctor(doctorId)
        }
      }.map {
        case Some(id) => Right(id)
        case None => Left(ServiceError.BadRequest("No doctor available at the requested time"): ServiceError)
      }
    )
  }

  def confirmAppointment(token: String): EitherT[IO, ServiceError, AppointmentDTO] = {
    val tokenHash = Sha256Service.hash(token)
    for {
      appointment <- EitherT.fromOptionF(
        appointmentRepo.readByConfirmationToken(tokenHash),
        ServiceError.NotFound("Appointment not found or already confirmed"): ServiceError
      )
      _ <- EitherT.cond[IO](
        appointment.status == "PENDING",
        (),
        ServiceError.BadRequest("Appointment is not pending"): ServiceError
      )
      _ <- EitherT(appointmentRepo.confirmAppointment(appointment.id.get).map {
        case 0 => Left(ServiceError.InternalError("Could not confirm appointment"): ServiceError)
        case _ => Right(())
      })
      confirmedAppointment <- readAppointment(appointment.id.get)
    } yield confirmedAppointment
  }

  def readAppointment(id: UUID): EitherT[IO, ServiceError, AppointmentDTO] =
    EitherT.fromOptionF(appointmentRepo.readById(id), ServiceError.NotFound("Appointment not found"): ServiceError)

  def readPatientAppointments(patientId: UUID, offset: Int, size: Int): EitherT[IO, ServiceError, List[AppointmentDTO]] =
    EitherT(appointmentRepo.readByPatient(patientId, offset, size).map(_.asRight[ServiceError]))

  def readDoctorAppointments(doctorId: UUID, offset: Int, size: Int): EitherT[IO, ServiceError, List[AppointmentDTO]] =
    EitherT(appointmentRepo.readByDoctor(doctorId, offset, size).map(_.asRight[ServiceError]))

  def readDoctorScheduleForDate(doctorId: UUID, date: Instant): EitherT[IO, ServiceError, List[AppointmentDTO]] =
    EitherT(appointmentRepo.readByDoctorAndDate(doctorId, date).map(_.asRight[ServiceError]))

  def readOfficeAppointments(officeId: UUID, offset: Int, size: Int): EitherT[IO, ServiceError, List[AppointmentDTO]] =
    EitherT(appointmentRepo.readByOffice(officeId, offset, size).map(_.asRight[ServiceError]))

  def updateAppointmentStatus(id: UUID, status: String, userId: UUID, userRole: Int): EitherT[IO, ServiceError, Unit] = {
    val validStatuses = Set("PENDING", "CONFIRMED", "REJECTED", "COMPLETED", "CANCELLED", "NOSHOW")
    for {
      _ <- EitherT.cond[IO](
        validStatuses.contains(status),
        (),
        ServiceError.BadRequest(s"Invalid status: $status"): ServiceError
      )
      appointment <- readAppointment(id)
      _ <- EitherT.cond[IO](
        userRole <= 1 || appointment.patientId == userId,
        (),
        ServiceError.Forbidden("Not authorized to update this appointment"): ServiceError
      )
      _ <- EitherT(appointmentRepo.updateStatus(id, status).map {
        case 0 => Left(ServiceError.NotFound("Appointment not found"): ServiceError)
        case _ => Right(())
      })
    } yield ()
  }

  def cancelAppointment(id: UUID, userId: UUID, userRole: Int): EitherT[IO, ServiceError, Unit] =
    updateAppointmentStatus(id, "CANCELLED", userId, userRole)

  def getPendingReminders(hoursAhead: Int = 24): EitherT[IO, ServiceError, List[AppointmentDTO]] = {
    val reminderTime = Instant.now().plusSeconds(hoursAhead * 3600)
    EitherT(appointmentRepo.readPendingReminders(reminderTime).map(_.asRight[ServiceError]))
  }

  def markReminderSent(id: UUID): EitherT[IO, ServiceError, Unit] =
    EitherT(appointmentRepo.markReminderSent(id).map {
      case 0 => Left(ServiceError.NotFound("Appointment not found"): ServiceError)
      case _ => Right(())
    })

  def getAvailableSlots(
    officeId: UUID,
    serviceId: UUID,
    date: Instant
  ): EitherT[IO, ServiceError, List[AppointmentSlotDTO]] = {
    for {
      service <- EitherT.fromOptionF(
        serviceRepo.readById(serviceId),
        ServiceError.NotFound("Service not found"): ServiceError
      )
      doctors <- EitherT.liftF(doctorRepo.getDoctorsForService(serviceId, officeId))
      dayOfWeek = ZonedDateTime.ofInstant(date, ZoneId.systemDefault()).getDayOfWeek.getValue % 7
      slots <- EitherT.liftF(
        doctors.flatTraverse { doctor =>
          generateSlotsForDoctor(doctor.id.get, s"${doctor.firstName} ${doctor.lastName}", dayOfWeek, date, service.durationInMinutes)
        }
      )
    } yield slots.sortBy(_.time)
  }

  private def generateSlotsForDoctor(
    doctorId: UUID,
    doctorName: String,
    dayOfWeek: Int,
    date: Instant,
    durationMinutes: Int
  ): IO[List[AppointmentSlotDTO]] = {
    scheduleRepo.readByDoctorAndDay(doctorId, dayOfWeek).flatMap {
      case None => IO.pure(List.empty)
      case Some(schedule) =>
        val baseDate = ZonedDateTime.ofInstant(date, ZoneId.systemDefault()).toLocalDate
        var currentTime = schedule.startTime
        val slots = scala.collection.mutable.ListBuffer[AppointmentSlotDTO]()

        while (currentTime.plusMinutes(durationMinutes).compareTo(schedule.endTime) <= 0) {
          val slotStart = baseDate.atTime(currentTime).atZone(ZoneId.systemDefault()).toInstant
          val slotEnd = slotStart.plusSeconds(durationMinutes * 60)
          slots += AppointmentSlotDTO(
            time = slotStart,
            doctorId = doctorId,
            doctorName = doctorName,
            isAvailable = true
          )
          currentTime = currentTime.plusMinutes(schedule.slotDurationMin)
        }

        appointmentRepo.readByDoctorAndDate(doctorId, date).map { existingAppointments =>
          slots.toList.map { slot =>
            val hasConflict = existingAppointments.exists { appt =>
              val apptEnd = appt.endTime.getOrElse(appt.appointmentTime.plusSeconds(durationMinutes * 60))
              !(slot.time.plusSeconds(durationMinutes * 60).compareTo(appt.appointmentTime) <= 0 ||
                slot.time.compareTo(apptEnd) >= 0)
            }
            slot.copy(isAvailable = !hasConflict)
          }
        }
    }
  }

  private implicit class ListOps[A](list: List[A]) {
    def flatTraverse[B](f: A => IO[List[B]]): IO[List[B]] =
      list.foldLeft(IO.pure(List.empty[B])) { (accIO, a) =>
        for {
          acc <- accIO
          result <- f(a)
        } yield acc ++ result
      }
  }
}
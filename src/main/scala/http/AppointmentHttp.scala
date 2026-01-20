package http

import cats.data.EitherT
import cats.effect.IO
import config.objects.NetworkConfig
import error.ServiceError
import io.circe.syntax.*
import io.circe.Json
import java.time.Instant
import java.util.UUID
import nats.EventBus
import nats.NatsEvent
import natstools.events.EmailEvent
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.HttpRoutes
import service.AppointmentService
import service.DoctorService
import utils.JwtService
import utils.UserRanks
import DTO.AppointmentDTO
import DTO.BookAppointmentDTO

final case class AppointmentHttp(
)(implicit
  appointmentService: AppointmentService,
  doctorService: DoctorService,
  jwtService: JwtService,
  client: Client[IO],
  eventBus: EventBus,
  networkConfig: NetworkConfig
) {

  private object OffsetMatcher extends QueryParamDecoderMatcher[Int]("offset")
  private object SizeMatcher extends QueryParamDecoderMatcher[Int]("size")
  private object DateMatcher extends QueryParamDecoderMatcher[String]("date")

  def routes(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case req @ POST -> Root / "appointment" =>
        req.as[BookAppointmentDTO].flatMap { bookingData =>
          val result = for {
            (jwtClaims, refreshResult) <-
              HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)

            patientId = UUID.fromString(jwtClaims.getSubject)
            patientEmail = Option(jwtClaims.getStringClaim("email")).getOrElse("")
            patientName = Option(jwtClaims.getStringClaim("firstName")).getOrElse("Patient")

            (appointmentId, confirmationToken) <-
              appointmentService.bookAppointment(patientId, bookingData)
            appointment <- appointmentService.readAppointment(appointmentId)

            emailEvent = NatsEvent.create[EmailEvent]((id, ts) =>
              EmailEvent(
                id,
                ts,
                patientEmail,
                EmailEvent.PURPOSE_CONFIRM,
                Map(
                  "name" -> patientName,
                  "appointmentId" -> appointmentId.toString,
                  "confirmationToken" -> confirmationToken,
                  "appointmentTime" -> appointment.appointmentTime.toString,
                  "doctorName" -> appointment.doctorName.getOrElse(""),
                  "serviceName" -> appointment.serviceName.getOrElse(""),
                  "officeName" -> appointment.officeName.getOrElse("")
                )
              )
            )
            _ <- HttpUtils.httpPublishEvent(emailEvent, "Failed to send confirmation email")
          } yield ((appointmentId, confirmationToken), refreshResult)

          result.fold(
            err => ErrorMapper.toResponse(err),
            success => {
              val ((appointmentId, _), refreshResult) = success
              HttpUtils.handleTokenRefresh(
                Created(
                  Json.obj(
                    "id" -> appointmentId.toString.asJson,
                    "message" -> "Appointment booked successfully. Please check your email to confirm.".asJson
                  )
                ),
                refreshResult
              )
            }
          ).flatten
        }

      case GET -> Root / "appointment" / "confirm" / token =>
        val result = for {
          appointment <- appointmentService.confirmAppointment(token)
        } yield appointment

        result.fold(
          err => ErrorMapper.toResponse(err),
          appointment =>
            Ok(
              Json.obj(
                "message" -> "Appointment confirmed successfully".asJson,
                "appointment" -> appointment.asJson
              )
            )
        ).flatten

      case req @ GET -> Root / "appointment" / "my" :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (jwtClaims, refreshResult) <-
            HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          patientId = UUID.fromString(jwtClaims.getSubject)
          appointments <- appointmentService.readPatientAppointments(patientId, offset, size)
        } yield (appointments, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (appointments, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(appointments.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "appointment" / "doctor" :? OffsetMatcher(offset) +& SizeMatcher(
            size
          ) =>
        val result = for {
          (jwtClaims, refreshResult) <-
            HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
          userId = UUID.fromString(jwtClaims.getSubject)
          doctor <- doctorService.readDoctorByUserId(userId)
          doctorId <- EitherT.fromOption[IO](
            doctor.id,
            ServiceError.InternalError("Doctor ID not found"): ServiceError
          )
          appointments <- appointmentService.readDoctorAppointments(doctorId, offset, size)
        } yield (appointments, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (appointments, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(appointments.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "appointment" / "doctor" / "schedule" :? DateMatcher(dateStr) =>
        val result = for {
          (jwtClaims, refreshResult) <-
            HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
          userId = UUID.fromString(jwtClaims.getSubject)
          doctor <- doctorService.readDoctorByUserId(userId)
          doctorId <- EitherT.fromOption[IO](
            doctor.id,
            ServiceError.InternalError("Doctor ID not found"): ServiceError
          )
          date = Instant.parse(dateStr + "T00:00:00Z")
          appointments <- appointmentService.readDoctorScheduleForDate(doctorId, date)
        } yield (appointments, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (appointments, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(appointments.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "appointment" / "office" / UUIDVar(officeId) :? OffsetMatcher(
            offset
          ) +& SizeMatcher(size) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          appointments       <- appointmentService.readOfficeAppointments(officeId, offset, size)
        } yield (appointments, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (appointments, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(appointments.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "appointment" / UUIDVar(appointmentId) =>
        val result = for {
          (jwtClaims, refreshResult) <-
            HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          userId = UUID.fromString(jwtClaims.getSubject)
          role = jwtClaims.getStringClaim("role").toIntOption.getOrElse(2)
          appointment <- appointmentService.readAppointment(appointmentId)
          _ <- EitherT.cond[IO](
            role <= UserRanks.DOCTOR || appointment.patientId == userId,
            (),
            ServiceError.Forbidden("Not authorized to view this appointment"): ServiceError
          )
        } yield (appointment, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (appointment, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(appointment.asJson), refreshResult)
          }
        ).flatten

      case req @ PUT -> Root / "appointment" / UUIDVar(appointmentId) / "status" / status =>
        val result = for {
          (jwtClaims, refreshResult) <-
            HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          userId = UUID.fromString(jwtClaims.getSubject)
          role = jwtClaims.getStringClaim("role").toIntOption.getOrElse(2)
          _ <- appointmentService.updateAppointmentStatus(appointmentId, status, userId, role)
        } yield ((), refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (_, refreshResult) = success
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> s"Appointment status updated to $status".asJson)),
              refreshResult
            )
          }
        ).flatten

      case req @ DELETE -> Root / "appointment" / UUIDVar(appointmentId) =>
        val result = for {
          (jwtClaims, refreshResult) <-
            HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          userId = UUID.fromString(jwtClaims.getSubject)
          role = jwtClaims.getStringClaim("role").toIntOption.getOrElse(2)
          appointment <- appointmentService.readAppointment(appointmentId)

          _ <- appointmentService.cancelAppointment(appointmentId, userId, role)

          cancelEvent = NatsEvent.create[EmailEvent]((id, ts) =>
            EmailEvent(
              id,
              ts,
              appointment.patientEmail.getOrElse(""),
              EmailEvent.PURPOSE_CANCELLED,
              Map(
                "name" -> appointment.patientName.getOrElse("Patient"),
                "appointmentTime" -> appointment.appointmentTime.toString,
                "doctorName" -> appointment.doctorName.getOrElse(""),
                "serviceName" -> appointment.serviceName.getOrElse("")
              )
            )
          )
          _ <- HttpUtils.httpPublishEvent(cancelEvent, "Failed to send cancellation email")
        } yield ((), refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (_, refreshResult) = success
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> "Appointment cancelled successfully".asJson)),
              refreshResult
            )
          }
        ).flatten

      case req @ GET -> Root / "appointment" / "slots" / UUIDVar(officeId) / UUIDVar(
            serviceId
          ) :? DateMatcher(dateStr) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          date = Instant.parse(dateStr + "T00:00:00Z")
          slots <- appointmentService.getAvailableSlots(officeId, serviceId, date)
        } yield (slots, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (slots, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(slots.asJson), refreshResult)
          }
        ).flatten

    }
}

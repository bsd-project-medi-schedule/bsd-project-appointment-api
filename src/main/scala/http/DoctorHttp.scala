package http

import cats.data.EitherT
import cats.effect.IO
import config.objects.NetworkConfig
import error.ServiceError
import io.circe.syntax.*
import io.circe.Json
import nats.EventBus
import nats.NatsEvent
import natstools.events.{AppointmentEmailEvent, DoctorCreatedEvent}
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Response}
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.http4s.multipart.Multipart
import service.DoctorService
import utils.{ExcelService, JwtService, UserRanks}
import DTO.{DoctorDTO, ScheduleCreateDTO, ScheduleDTO}

import java.io.ByteArrayInputStream
import java.time.LocalTime
import java.util.UUID

final case class DoctorHttp(
  doctorService: DoctorService
)(implicit
  jwtService: JwtService,
  eventBus: EventBus,
  networkConfig: NetworkConfig
) {

  private object OffsetMatcher extends QueryParamDecoderMatcher[Int]("offset")
  private object SizeMatcher extends QueryParamDecoderMatcher[Int]("size")

  def routes(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case req @ POST -> Root / "doctor" =>
        req.as[DoctorDTO].flatMap { doctorData =>
          val result = for {
            (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            doctorId <- doctorService.createDoctor(doctorData)
          } yield doctorId

          result.fold(
            err => ErrorMapper.toResponse(err),
            doctorId => Created(Json.obj("id" -> doctorId.toString.asJson, "message" -> "Doctor created successfully".asJson))
          ).flatten
        }

      case req @ POST -> Root / "doctor" / "import" / UUIDVar(officeId) =>
        req.decode[Multipart[IO]] { multipart =>
          val result = for {
            (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            filePart <- EitherT.fromOption[IO](
              multipart.parts.find(_.name.contains("file")),
              ServiceError.BadRequest("No file uploaded"): ServiceError
            )
            bytes <- EitherT.liftF(filePart.body.compile.to(Array))
            doctors <- EitherT.liftF(ExcelService.parseDoctorsFromExcel(new ByteArrayInputStream(bytes)))
            _ <- EitherT.cond[IO](
              doctors.nonEmpty,
              (),
              ServiceError.BadRequest("No valid doctors found in Excel file"): ServiceError
            )
            createdIds <- doctors.foldLeft(EitherT.rightT[IO, ServiceError](List.empty[UUID])) { (accEither, importDto) =>
              for {
                acc <- accEither
                userId = UUID.randomUUID()
                doctorDto = DoctorDTO(
                  userId = userId,
                  officeId = officeId,
                  fieldOfAction = importDto.fieldOfAction,
                  firstName = importDto.firstName,
                  lastName = importDto.lastName,
                  email = importDto.email,
                  phone = importDto.phone
                )
                doctorId <- doctorService.createDoctor(doctorDto)

                doctorEvent = NatsEvent.create[DoctorCreatedEvent]((id, ts) =>
                  DoctorCreatedEvent(id, ts, importDto.email, importDto.password, importDto.firstName, importDto.lastName)
                )
                welcomeEvent = NatsEvent.create[AppointmentEmailEvent]((id, ts) =>
                  AppointmentEmailEvent(id, ts, importDto.email, AppointmentEmailEvent.PURPOSE_DOCTOR_WELCOME,
                    Map("name" -> s"${importDto.firstName} ${importDto.lastName}", "field" -> importDto.fieldOfAction))
                )
                _ <- HttpUtils.httpPublishEvent(Seq(doctorEvent, welcomeEvent), "Failed to send doctor events")
              } yield acc :+ doctorId
            }
          } yield createdIds

          result.fold(
            err => ErrorMapper.toResponse(err),
            ids => Created(Json.obj(
              "message" -> s"${ids.size} doctors imported successfully".asJson,
              "ids" -> ids.map(_.toString).asJson
            ))
          ).flatten
        }

      case req @ GET -> Root / "doctor" / "excel-template" =>
        val result: EitherT[IO, ServiceError, Array[Byte]] = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          excelBytes <- EitherT.liftF[IO, ServiceError, Array[Byte]](ExcelService.generateSampleExcel())
        } yield excelBytes

        result.fold(
          err => ErrorMapper.toResponse(err),
          bytes => Ok(bytes).map(_.withContentType(`Content-Type`(MediaType.application.`vnd.openxmlformats-officedocument.spreadsheetml.sheet`)))
        ).flatten

      case req @ GET -> Root / "doctor" :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          doctors <- doctorService.readDoctors(offset, size)
        } yield doctors

        result.fold(
          err => ErrorMapper.toResponse(err),
          doctors => Ok(doctors.asJson)
        ).flatten

      case req @ GET -> Root / "doctor" / "office" / UUIDVar(officeId) :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          doctors <- doctorService.readDoctorsByOffice(officeId, offset, size)
        } yield doctors

        result.fold(
          err => ErrorMapper.toResponse(err),
          doctors => Ok(doctors.asJson)
        ).flatten

      case req @ GET -> Root / "doctor" / UUIDVar(doctorId) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          doctor <- doctorService.readDoctor(doctorId)
        } yield doctor

        result.fold(
          err => ErrorMapper.toResponse(err),
          doctor => Ok(doctor.asJson)
        ).flatten

      case req @ GET -> Root / "doctor" / "me" =>
        val result = for {
          (jwtClaims, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
          userId = UUID.fromString(jwtClaims.getSubject)
          doctor <- doctorService.readDoctorByUserId(userId)
        } yield doctor

        result.fold(
          err => ErrorMapper.toResponse(err),
          doctor => Ok(doctor.asJson)
        ).flatten

      case req @ PUT -> Root / "doctor" / UUIDVar(doctorId) =>
        req.as[DoctorDTO].flatMap { doctorData =>
          val result = for {
            (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            _ <- doctorService.updateDoctor(doctorId, doctorData)
          } yield ()

          result.fold(
            err => ErrorMapper.toResponse(err),
            _ => Ok(Json.obj("message" -> "Doctor updated successfully".asJson))
          ).flatten
        }

      case req @ DELETE -> Root / "doctor" / UUIDVar(doctorId) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          _ <- doctorService.deleteDoctor(doctorId)
        } yield ()

        result.fold(
          err => ErrorMapper.toResponse(err),
          _ => Ok(Json.obj("message" -> "Doctor deleted successfully".asJson))
        ).flatten

      case req @ POST -> Root / "doctor" / UUIDVar(doctorId) / "service" / UUIDVar(serviceId) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          _ <- doctorService.addServiceToDoctor(doctorId, serviceId)
        } yield ()

        result.fold(
          err => ErrorMapper.toResponse(err),
          _ => Ok(Json.obj("message" -> "Service added to doctor successfully".asJson))
        ).flatten

      case req @ DELETE -> Root / "doctor" / UUIDVar(doctorId) / "service" / UUIDVar(serviceId) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          _ <- doctorService.removeServiceFromDoctor(doctorId, serviceId)
        } yield ()

        result.fold(
          err => ErrorMapper.toResponse(err),
          _ => Ok(Json.obj("message" -> "Service removed from doctor successfully".asJson))
        ).flatten

      case req @ POST -> Root / "doctor" / UUIDVar(doctorId) / "schedule" =>
        req.as[ScheduleCreateDTO].flatMap { scheduleData =>
          val result = for {
            (jwtClaims, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
            userId = UUID.fromString(jwtClaims.getSubject)
            role = jwtClaims.getStringClaim("role").toIntOption.getOrElse(2)
            doctor <- doctorService.readDoctor(doctorId)
            _ <- EitherT.cond[IO](
              role == UserRanks.ADMIN || doctor.userId == userId,
              (),
              ServiceError.Forbidden("Not authorized to modify this schedule"): ServiceError
            )
            schedule = ScheduleDTO(
              doctorId = doctorId,
              dayOfWeek = scheduleData.dayOfWeek,
              startTime = LocalTime.parse(scheduleData.startTime),
              endTime = LocalTime.parse(scheduleData.endTime),
              slotDurationMin = scheduleData.slotDurationMin
            )
            scheduleId <- doctorService.createSchedule(schedule)
          } yield scheduleId

          result.fold(
            err => ErrorMapper.toResponse(err),
            scheduleId => Created(Json.obj("id" -> scheduleId.toString.asJson, "message" -> "Schedule created successfully".asJson))
          ).flatten
        }

      case req @ GET -> Root / "doctor" / UUIDVar(doctorId) / "schedule" =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          schedules <- doctorService.getSchedule(doctorId)
        } yield schedules

        result.fold(
          err => ErrorMapper.toResponse(err),
          schedules => Ok(schedules.asJson)
        ).flatten

      case req @ DELETE -> Root / "doctor" / "schedule" / UUIDVar(scheduleId) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
          _ <- doctorService.deleteSchedule(scheduleId)
        } yield ()

        result.fold(
          err => ErrorMapper.toResponse(err),
          _ => Ok(Json.obj("message" -> "Schedule deleted successfully".asJson))
        ).flatten

    }
}
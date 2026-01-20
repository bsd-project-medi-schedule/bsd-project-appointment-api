package http

import cats.data.EitherT
import cats.effect.IO
import config.objects.NetworkConfig
import error.ServiceError
import io.circe.syntax.*
import io.circe.Json
import nats.EventBus
import nats.NatsEvent
import natstools.events.{DoctorCreatedEvent, EmailEvent}
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
import org.http4s.client.Client

import java.io.ByteArrayInputStream
import java.time.LocalTime
import java.util.UUID

final case class DoctorHttp(
                           )(implicit
                             doctorService: DoctorService,
                             jwtService: JwtService,
                             client: Client[IO],
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
            (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            doctorId <- doctorService.createDoctor(doctorData)
          } yield (doctorId, refreshResult)

          result.fold(
            err => ErrorMapper.toResponse(err),
            success => {
              val (doctorId, refreshResult) = success
              HttpUtils.handleTokenRefresh(
                Created(
                  Json.obj(
                    "id" -> doctorId.toString.asJson,
                    "message" -> "Doctor created successfully".asJson
                  )
                ),
                refreshResult
              )
            }
          ).flatten
        }

      case req @ POST -> Root / "doctor" / "import" / UUIDVar(officeId) =>
        req.decode[Multipart[IO]] { multipart =>
          val result = for {
            (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
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
                welcomeEvent = NatsEvent.create[EmailEvent]((id, ts) =>
                  EmailEvent(
                    id,
                    ts,
                    importDto.email,
                    EmailEvent.PURPOSE_DOCTOR_WELCOME,
                    Map(
                      "name" -> s"${importDto.firstName} ${importDto.lastName}",
                      "field" -> importDto.fieldOfAction
                    )
                  )
                )
                _ <- HttpUtils.httpPublishEvent(Seq(doctorEvent, welcomeEvent), "Failed to send doctor events")
              } yield acc :+ doctorId
            }
          } yield (createdIds, refreshResult)

          result.fold(
            err => ErrorMapper.toResponse(err),
            success => {
              val (ids, refreshResult) = success
              HttpUtils.handleTokenRefresh(
                Created(
                  Json.obj(
                    "message" -> s"${ids.size} doctors imported successfully".asJson,
                    "ids" -> ids.map(_.toString).asJson
                  )
                ),
                refreshResult
              )
            }
          ).flatten
        }

      case req @ GET -> Root / "doctor" / "excel-template" =>
        val result: EitherT[IO, ServiceError, (Array[Byte], Any)] = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          excelBytes <- EitherT.liftF[IO, ServiceError, Array[Byte]](ExcelService.generateSampleExcel())
        } yield (excelBytes, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (bytes, refreshResult) = success
              Ok(bytes).map(_.withContentType(`Content-Type`(MediaType.application.`vnd.openxmlformats-officedocument.spreadsheetml.sheet`)))
          }
        ).flatten

      case req @ GET -> Root / "doctor" :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          doctors <- doctorService.readDoctors(offset, size)
        } yield (doctors, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (doctors, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(doctors.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "doctor" / "office" / UUIDVar(officeId) :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          doctors <- doctorService.readDoctorsByOffice(officeId, offset, size)
        } yield (doctors, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (doctors, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(doctors.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "doctor" / UUIDVar(doctorId) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          doctor <- doctorService.readDoctor(doctorId)
        } yield (doctor, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (doctor, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(doctor.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "doctor" / "me" =>
        val result = for {
          (jwtClaims, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
          userId = UUID.fromString(jwtClaims.getSubject)
          doctor <- doctorService.readDoctorByUserId(userId)
        } yield (doctor, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (doctor, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(doctor.asJson), refreshResult)
          }
        ).flatten

      case req @ PUT -> Root / "doctor" / UUIDVar(doctorId) =>
        req.as[DoctorDTO].flatMap { doctorData =>
          val result = for {
            (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            _ <- doctorService.updateDoctor(doctorId, doctorData)
          } yield ((), refreshResult)

          result.fold(
            err => ErrorMapper.toResponse(err),
            success => {
              val (_, refreshResult) = success
              HttpUtils.handleTokenRefresh(
                Ok(Json.obj("message" -> "Doctor updated successfully".asJson)),
                refreshResult
              )
            }
          ).flatten
        }

      case req @ DELETE -> Root / "doctor" / UUIDVar(doctorId) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          _ <- doctorService.deleteDoctor(doctorId)
        } yield ((), refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (_, refreshResult) = success
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> "Doctor deleted successfully".asJson)),
              refreshResult
            )
          }
        ).flatten

      case req @ POST -> Root / "doctor" / UUIDVar(doctorId) / "service" / UUIDVar(serviceId) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          _ <- doctorService.addServiceToDoctor(doctorId, serviceId)
        } yield ((), refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (_, refreshResult) = success
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> "Service added to doctor successfully".asJson)),
              refreshResult
            )
          }
        ).flatten

      case req @ DELETE -> Root / "doctor" / UUIDVar(doctorId) / "service" / UUIDVar(serviceId) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          _ <- doctorService.removeServiceFromDoctor(doctorId, serviceId)
        } yield ((), refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (_, refreshResult) = success
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> "Service removed from doctor successfully".asJson)),
              refreshResult
            )
          }
        ).flatten

      case req @ POST -> Root / "doctor" / UUIDVar(doctorId) / "schedule" =>
        req.as[ScheduleCreateDTO].flatMap { scheduleData =>
          val result = for {
            (jwtClaims, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
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
          } yield (scheduleId, refreshResult)

          result.fold(
            err => ErrorMapper.toResponse(err),
            success => {
              val (scheduleId, refreshResult) = success
              HttpUtils.handleTokenRefresh(
                Created(
                  Json.obj(
                    "id" -> scheduleId.toString.asJson,
                    "message" -> "Schedule created successfully".asJson
                  )
                ),
                refreshResult
              )
            }
          ).flatten
        }

      case req @ GET -> Root / "doctor" / UUIDVar(doctorId) / "schedule" =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          schedules <- doctorService.getSchedule(doctorId)
        } yield (schedules, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (schedules, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(schedules.asJson), refreshResult)
          }
        ).flatten

      case req @ DELETE -> Root / "doctor" / "schedule" / UUIDVar(scheduleId) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.DOCTOR)
          _ <- doctorService.deleteSchedule(scheduleId)
        } yield ((), refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (_, refreshResult) = success
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> "Schedule deleted successfully".asJson)),
              refreshResult
            )
          }
        ).flatten

    }
}

package http

import cats.effect.IO
import config.objects.NetworkConfig
import io.circe.syntax.*
import io.circe.Json
import nats.EventBus
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.dsl.io.*
import org.http4s.HttpRoutes
import service.{OfficeService, ServiceService}
import utils.JwtService
import utils.UserRanks
import DTO.{OfficeDTO, ServiceCreateDTO}
import org.http4s.client.Client

import java.util.UUID

final case class OfficeHttp(
)(implicit
  officeService: OfficeService,
  serviceService: ServiceService,
  jwtService: JwtService,
  client: Client[IO],
  eventBus: EventBus,
  networkConfig: NetworkConfig
) {

  private object OffsetMatcher extends QueryParamDecoderMatcher[Int]("offset")
  private object SizeMatcher extends QueryParamDecoderMatcher[Int]("size")

  def routes(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case req @ POST -> Root / "office" =>
        req.as[OfficeDTO].flatMap { officeData =>
          val result = for {
            (_, needsRefresh) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            officeId <- officeService.createOffice(officeData)
          } yield (officeId, needsRefresh)

          result.fold(
            err => ErrorMapper.toResponse(err),
            res => {
              val (officeId, _) = res
              Created(Json.obj("id" -> officeId.toString.asJson, "message" -> "Office created successfully".asJson))
            }
          ).flatten
        }

      case req @ GET -> Root / "office" :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          offices <- officeService.readOffices(offset, size)
        } yield offices

        result.fold(
          err => ErrorMapper.toResponse(err),
          offices => Ok(offices.asJson)
        ).flatten

      case req @ GET -> Root / "office" / UUIDVar(officeId) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          office <- officeService.readOffice(officeId)
        } yield office

        result.fold(
          err => ErrorMapper.toResponse(err),
          office => Ok(office.asJson)
        ).flatten

      case req @ PUT -> Root / "office" / UUIDVar(officeId) =>
        req.as[OfficeDTO].flatMap { officeData =>
          val result = for {
            (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            _ <- officeService.updateOffice(officeId, officeData)
          } yield ()

          result.fold(
            err => ErrorMapper.toResponse(err),
            _ => Ok(Json.obj("message" -> "Office updated successfully".asJson))
          ).flatten
        }

      case req @ DELETE -> Root / "office" / UUIDVar(officeId) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          _ <- officeService.deleteOffice(officeId)
        } yield ()

        result.fold(
          err => ErrorMapper.toResponse(err),
          _ => Ok(Json.obj("message" -> "Office deleted successfully".asJson))
        ).flatten

      case req @ POST -> Root / "office" / UUIDVar(officeId) / "service" =>
        req.as[ServiceCreateDTO].flatMap { serviceData =>
          val result = for {
            (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            serviceId <- serviceService.createService(officeId, serviceData)
          } yield serviceId

          result.fold(
            err => ErrorMapper.toResponse(err),
            serviceId => Created(Json.obj("id" -> serviceId.toString.asJson, "message" -> "Service created successfully".asJson))
          ).flatten
        }

      case req @ GET -> Root / "office" / UUIDVar(officeId) / "service" :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (_, _) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          services <- serviceService.readServicesByOffice(officeId, offset, size)
        } yield services

        result.fold(
          err => ErrorMapper.toResponse(err),
          services => Ok(services.asJson)
        ).flatten

    }
}
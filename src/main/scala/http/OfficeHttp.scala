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
            (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            officeId <- officeService.createOffice(officeData)
          } yield (officeId, refreshResult)

          result.fold(
            err => ErrorMapper.toResponse(err),
            success => {
              val (officeId, refreshResult) = success
              HttpUtils.handleTokenRefresh(
                Created(
                  Json.obj(
                    "id" -> officeId.toString.asJson,
                    "message" -> "Office created successfully".asJson
                  )
                ),
                refreshResult
              )
            }
          ).flatten
        }

      case req @ GET -> Root / "office" :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          offices <- officeService.readOffices(offset, size)
        } yield (offices, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (offices, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(offices.asJson), refreshResult)
          }
        ).flatten

      case req @ GET -> Root / "office" / UUIDVar(officeId) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          office <- officeService.readOffice(officeId)
        } yield (office, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (office, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(office.asJson), refreshResult)
          }
        ).flatten

      case req @ PUT -> Root / "office" / UUIDVar(officeId) =>
        req.as[OfficeDTO].flatMap { officeData =>
          val result = for {
            (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            _ <- officeService.updateOffice(officeId, officeData)
          } yield ((), refreshResult)

          result.fold(
            err => ErrorMapper.toResponse(err),
            success => {
              val (_, refreshResult) = success
              HttpUtils.handleTokenRefresh(
                Ok(Json.obj("message" -> "Office updated successfully".asJson)),
                refreshResult
              )
            }
          ).flatten
        }

      case req @ DELETE -> Root / "office" / UUIDVar(officeId) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
          _ <- officeService.deleteOffice(officeId)
        } yield ((), refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (_, refreshResult) = success
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> "Office deleted successfully".asJson)),
              refreshResult
            )
          }
        ).flatten

      case req @ POST -> Root / "office" / UUIDVar(officeId) / "service" =>
        req.as[ServiceCreateDTO].flatMap { serviceData =>
          val result = for {
            (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.ADMIN)
            serviceId <- serviceService.createService(officeId, serviceData)
          } yield (serviceId, refreshResult)

          result.fold(
            err => ErrorMapper.toResponse(err),
            success => {
              val (serviceId, refreshResult) = success
              HttpUtils.handleTokenRefresh(
                Created(
                  Json.obj(
                    "id" -> serviceId.toString.asJson,
                    "message" -> "Service created successfully".asJson
                  )
                ),
                refreshResult
              )
            }
          ).flatten
        }

      case req @ GET -> Root / "office" / UUIDVar(officeId) / "service" :? OffsetMatcher(offset) +& SizeMatcher(size) =>
        val result = for {
          (_, refreshResult) <- HttpUtils.verifyTokenFromCookie(req.cookies, UserRanks.PATIENT)
          services <- serviceService.readServicesByOffice(officeId, offset, size)
        } yield (services, refreshResult)

        result.fold(
          err => ErrorMapper.toResponse(err),
          success => {
            val (services, refreshResult) = success
            HttpUtils.handleTokenRefresh(Ok(services.asJson), refreshResult)
          }
        ).flatten

    }
}

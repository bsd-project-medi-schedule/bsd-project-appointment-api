package http

import cats.data.EitherT
import cats.effect.IO
import config.objects.NetworkConfig
import error.ServiceError
import io.circe.syntax.*
import io.circe.Json
import nats.EventBus
import nats.NatsEvent
import natstools.events.RefreshTokenEvent
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.HttpRoutes
import utils.JwtService
import utils.NonceService
import utils.Sha256Service

import java.util.UUID

final case class AuthHttp()(implicit
  jwtService: JwtService,
  eventBus: EventBus,
  networkConfig: NetworkConfig
) {

  private object RoleMatcher extends QueryParamDecoderMatcher[Int]("role")

  def routes(): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case GET -> Root / "health" =>
        Ok(Json.obj("status" -> "healthy".asJson))

      case req @ GET -> Root / "auth" :? RoleMatcher(role) =>
        val result = for {
          (jwtClaims, needsRefresh) <- HttpUtils.verifyTokenFromCookie(req.cookies, role)
          userId = UUID.fromString(jwtClaims.getSubject)
          userRole = jwtClaims.getStringClaim("role").toIntOption.getOrElse(2)
          firstName = Option(jwtClaims.getStringClaim("firstName"))

          refreshResult <- if (needsRefresh) {
            val newJwt = jwtService.createToken(userId, userRole, firstName)
            val newRefresh = NonceService.generateNonce()
            val newRefreshHash = Sha256Service.hash(newRefresh)

            val event = NatsEvent.create[RefreshTokenEvent]((id, ts) =>
              RefreshTokenEvent(id, ts, userId, newRefreshHash)
            )
            HttpUtils.httpPublishEvent(event, "Failed to update refresh token")
              .map(_ => HttpUtils.TokenRefreshResult(Some(newJwt), Some(newRefresh)))
          } else {
            EitherT.rightT[IO, ServiceError](HttpUtils.TokenRefreshResult(None, None))
          }
        } yield refreshResult

        result.fold(
          err => ErrorMapper.toResponse(err),
          refreshResult =>
            HttpUtils.handleTokenRefresh(
              Ok(Json.obj("message" -> "Authentication passed".asJson)),
              refreshResult
            )
        ).flatten

      case req @ DELETE -> Root / "auth" =>
        HttpUtils.clearAuthCookies(Ok(Json.obj("message" -> "Logged out successfully".asJson)))

    }
}
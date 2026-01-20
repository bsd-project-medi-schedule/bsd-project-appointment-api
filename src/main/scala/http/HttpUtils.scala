package http

import cats.data.EitherT
import cats.effect.IO
import cats.implicits.toBifunctorOps
import com.nimbusds.jwt.JWTClaimsSet
import config.objects.NetworkConfig
import error.ServiceError
import http.HttpUtils.validateRefreshToken
import nats.EventBus
import nats.NatsEvent
import natstools.events.LoginSessionEvent
import org.http4s.client.Client
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.ResponseCookie
import org.http4s.SameSite
import org.http4s.Status
import org.http4s.Uri
import utils.JwtService
import utils.NonceService
import utils.Sha256Service
import DTO.LoginSessionDTO
import org.http4s.circe.toMessageSyntax

object HttpUtils {

  def httpPublishEvent(event: NatsEvent, err: String)(implicit
    eventBus: EventBus
  ): EitherT[IO, ServiceError, Unit] =
    EitherT(eventBus.publish(event).attempt.map(_.leftMap(_ => ServiceError.InternalError(err))))

  def httpPublishEvent(events: Seq[NatsEvent], err: String)(implicit
    eventBus: EventBus
  ): EitherT[IO, ServiceError, Unit] =
    EitherT(eventBus.publish(events).attempt.map(_.leftMap(_ => ServiceError.InternalError(err))))

  case class TokenRefreshResult(newJwt: Option[String], newRefresh: Option[String])

  private def validateRefreshToken(
    refreshToken: String
  )(implicit
    client: Client[IO],
    networkConfig: NetworkConfig
  ): EitherT[IO, ServiceError, LoginSessionDTO] = {
    val uriE: Either[ServiceError, Uri] =
      Uri.fromString(s"${networkConfig.authEndpoint}/$refreshToken").leftMap(_.message).leftMap(_ =>
        ServiceError.BadRequest("Bad auth url given")
      )
    for {
      uri <- EitherT.fromEither[IO](uriE)
      req = Request[IO](method = Method.GET, uri = uri)
      res <- EitherT {
        client.run(req).use { resp =>
          resp.status match {
            case Status.Ok =>
              resp
                .asJsonDecode[LoginSessionDTO]
                .attempt
                .map {
                  case Right(dto) => Right(dto)
                  case Left(e) =>
                    Left(ServiceError.BadRequest(s"Failed to decode LoginDTO: ${e.getMessage}"))
                }
            case _ =>
              resp.bodyText.compile.string.map { body =>
                Left(ServiceError.Unauthorized(
                  s"Refresh token validation failed: HTTP ${resp.status.code}. Body: $body"
                ))
              }
          }
        }
      }.leftWiden[ServiceError]
    } yield res
  }

  def verifyTokenFromCookie(
    cookies: List[org.http4s.RequestCookie],
    minRoleNeeded: Int,
  )(implicit
    jwtService: JwtService,
    networkConfig: NetworkConfig,
    eventBus: EventBus,
    client: Client[IO]
  ): EitherT[IO, ServiceError, (JWTClaimsSet, TokenRefreshResult)] =
    for {
      refresh <- EitherT.fromOption[IO](
        cookies.find(_.name == "refresh-token"),
        ServiceError.Forbidden("Refresh token cookie is missing")
      )
      refreshTokenHash = Sha256Service.hash(refresh.content)
      jwt <- EitherT.fromOption[IO](
        cookies.find(_.name == "auth-token"),
        ServiceError.Forbidden("Access token cookie is missing")
      )
      (jwtClaims, isExpired) <- EitherT.fromOption[IO](
        jwtService.verifyToken(jwt.content),
        ServiceError.Forbidden("Invalid access token")
      )
      role = jwtClaims.getStringClaim("role").toIntOption.getOrElse(2)
      results <- if (isExpired) for {
        loginSession <- validateRefreshToken(refreshTokenHash)
        newJwt =
          jwtService.createToken(loginSession.id, role, jwtClaims.getStringClaim("firstName"))
        newRefresh = NonceService.generateNonce()
        newRefreshHash = Sha256Service.hash(newRefresh)
        event = NatsEvent.create[LoginSessionEvent]((id, ts) =>
          LoginSessionEvent(id, ts, refreshTokenHash, newRefreshHash)
        )
        _ <- HttpUtils.httpPublishEvent(Seq(event), "Email service unavailable")
      } yield TokenRefreshResult(Some(newJwt), Some(newRefresh))
      else EitherT.rightT[IO, ServiceError](TokenRefreshResult(None, None))
      _ <- EitherT.cond[IO](
        role <= minRoleNeeded,
        (),
        ServiceError.Unauthorized("Incompatible jwt - refresh")
      ).leftWiden[ServiceError]
    } yield (jwtClaims, results)

  def handleTokenRefresh(req: IO[Response[IO]], refreshResult: TokenRefreshResult)(implicit
    networkConfig: NetworkConfig
  ): IO[Response[IO]] =
    (refreshResult.newJwt, refreshResult.newRefresh) match {
      case (Some(jwt), Some(refresh)) =>
        attachRefreshToReq(attachJwtToReq(req, jwt), refresh, rememberMe = true)
      case (Some(jwt), None) =>
        attachJwtToReq(req, jwt)
      case _ => req
    }

  def attachJwtToReq(req: IO[Response[IO]], jwt: String)(implicit
    networkConfig: NetworkConfig
  ): IO[Response[IO]] = {
    val cookieExpiry = 15 * 60

    val jwtCookie = ResponseCookie(
      name = "auth-token",
      content = jwt,
      maxAge = Some(cookieExpiry),
      httpOnly = true,
      secure = networkConfig.secureCookies,
      sameSite = Some(SameSite.None),
      path = Some("/")
    )
    req.map(_.addCookie(jwtCookie))
  }

  def attachRefreshToReq(req: IO[Response[IO]], refresh: String, rememberMe: Boolean)(implicit
    networkConfig: NetworkConfig
  ): IO[Response[IO]] = {
    val refreshCookie = if (rememberMe) {
      val cookieExpiry = 30 * 24 * 60 * 60

      ResponseCookie(
        name = "refresh-token",
        content = refresh,
        maxAge = Some(cookieExpiry),
        httpOnly = true,
        secure = networkConfig.secureCookies,
        sameSite = Some(SameSite.None),
        path = Some("/")
      )
    } else {
      ResponseCookie(
        name = "refresh-token",
        content = refresh,
        httpOnly = true,
        secure = networkConfig.secureCookies,
        sameSite = Some(SameSite.None),
        path = Some("/")
      )
    }

    req.map(_.addCookie(refreshCookie))
  }

  def clearAuthCookies(req: IO[Response[IO]])(implicit
    networkConfig: NetworkConfig
  ): IO[Response[IO]] = {
    val clearedJwtCookie = ResponseCookie(
      name = "auth-token",
      content = "",
      maxAge = Some(0),
      httpOnly = true,
      secure = networkConfig.secureCookies,
      sameSite = Some(SameSite.None),
      path = Some("/")
    )
    val clearedRefreshCookie = ResponseCookie(
      name = "refresh-token",
      content = "",
      maxAge = Some(0),
      httpOnly = true,
      secure = networkConfig.secureCookies,
      sameSite = Some(SameSite.None),
      path = Some("/")
    )
    req.map(_.addCookie(clearedJwtCookie).addCookie(clearedRefreshCookie))
  }

}

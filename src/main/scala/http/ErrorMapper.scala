package http

import cats.effect.IO
import io.circe.syntax._
import io.circe.Json
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.headers.`WWW-Authenticate`
import org.http4s.{Challenge, Response}
import error.ServiceError

object ErrorMapper {

  def toResponse(error: ServiceError): IO[Response[IO]] = error match {
    case ServiceError.NotFound(msg) =>
      NotFound(Json.obj("message" -> msg.asJson))
    case ServiceError.Forbidden(msg) =>
      Forbidden(Json.obj("message" -> msg.asJson))
    case ServiceError.BadRequest(msg) =>
      BadRequest(Json.obj("message" -> msg.asJson))
    case ServiceError.InternalError(msg) =>
      InternalServerError(Json.obj("message" -> msg.asJson))
    case ServiceError.Unauthorized(msg) =>
      Unauthorized(`WWW-Authenticate`(Challenge("Bearer", "api")), Json.obj("message" -> msg.asJson))
  }

}
package config.middleware

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import io.circe.Json
import io.circe.syntax._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.{HttpRoutes, Request, Response}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

object ErrorHandlerMiddleware {

  private val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def apply(routes: HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli { (req: Request[IO]) =>
      OptionT(
        routes.run(req).value.handleErrorWith { error =>
          logger.error(error)(s"Unhandled error on ${req.method} ${req.uri}") *>
            InternalServerError(Json.obj("message" -> "Internal server error".asJson)).map(Some(_))
        }
      )
    }
}
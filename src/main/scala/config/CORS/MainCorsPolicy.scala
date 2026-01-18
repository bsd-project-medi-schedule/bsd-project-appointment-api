package config.CORS

import cats.effect.IO
import config.objects.NetworkConfig
import org.http4s.Method.*
import org.http4s.headers.Origin
import org.http4s.server.middleware.CORS
import org.http4s.{HttpRoutes, Uri}
import org.typelevel.ci.CIStringSyntax

import scala.concurrent.duration.*

object MainCorsPolicy {

  def apply(routes: HttpRoutes[IO])(implicit cfg: NetworkConfig): HttpRoutes[IO] = {
    val trustedOrigins = cfg.trustedOrigins.map { origin =>
      val scheme = if (origin.ssl) Uri.Scheme.https else Uri.Scheme.http
      Origin.Host(scheme, Uri.RegName(origin.tHost), origin.tPort)
    }.toSet

    val policy = CORS.policy
      .withAllowOriginHost(trustedOrigins)
      .withAllowMethodsIn(Set(GET, POST, PUT, DELETE))
      .withAllowHeadersIn(Set(ci"Authorization", ci"Content-Type"))
      .withAllowCredentials(true)
      .withMaxAge(1.day)
    policy(routes)
  }
}

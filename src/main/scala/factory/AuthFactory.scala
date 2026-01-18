package factory

import cats.effect.IO
import cats.effect.Resource
import config.objects.NetworkConfig
import config.CORS.MainCorsPolicy
import http.AuthHttp
import nats.EventBus
import org.http4s.HttpRoutes
import utils.JwtService

final case class AuthFactory(
  authRoutes: HttpRoutes[IO]
)

object AuthFactory {
  def apply()(implicit
    jwtService: JwtService,
    eventBus: EventBus,
    networkConfig: NetworkConfig
  ): Resource[IO, AuthFactory] =
    Resource.eval(IO {
      val authHttp = AuthHttp()
      val authCors = MainCorsPolicy(authHttp.routes())

      AuthFactory(authCors)
    })
}
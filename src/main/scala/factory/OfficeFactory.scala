package factory

import cats.effect.IO
import cats.effect.Resource
import config.objects.NetworkConfig
import http.OfficeHttp
import nats.EventBus
import org.http4s.client.Client
import service.*
import utils.JwtService

object OfficeFactory {
  def apply(
    officeService: OfficeService,
    serviceService: ServiceService,
    jwtService: JwtService,
  )(implicit
    client: Client[IO],
    eventBus: EventBus,
    networkConfig: NetworkConfig
  ): Resource[IO, OfficeHttp] =
    Resource.eval(IO(OfficeHttp()(
      officeService,
      serviceService,
      jwtService,
      client,
      eventBus,
      networkConfig
    )))
}

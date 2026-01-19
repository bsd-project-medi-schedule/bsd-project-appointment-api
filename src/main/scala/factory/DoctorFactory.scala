package factory

import cats.effect.IO
import cats.effect.Resource
import config.objects.NetworkConfig
import http.DoctorHttp
import nats.EventBus
import org.http4s.client.Client
import service.*
import utils.JwtService

object DoctorFactory {
  def apply(doctorService: DoctorService, jwtService: JwtService)(implicit
    client: Client[IO],
    eventBus: EventBus,
    networkConfig: NetworkConfig
  ): Resource[IO, DoctorHttp] =
    Resource.eval(IO(DoctorHttp()(doctorService, jwtService, client, eventBus, networkConfig)))
}

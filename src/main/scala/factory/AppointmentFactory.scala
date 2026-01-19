package factory

import cats.effect.IO
import cats.effect.Resource
import config.objects.NetworkConfig
import config.CORS.MainCorsPolicy
import doobie.Transactor
import http.AppointmentHttp
import http.DoctorHttp
import http.OfficeHttp
import impl.*
import nats.EventBus
import org.http4s.HttpRoutes
import org.http4s.client.Client
import service.*
import utils.JwtService

object AppointmentFactory {
  def apply(
    appointmentService: AppointmentService,
    doctorService: DoctorService,
    jwtService: JwtService
  )(implicit
    client: Client[IO],
    eventBus: EventBus,
    networkConfig: NetworkConfig
  ): Resource[IO, AppointmentHttp] =
    Resource.eval(IO {
      AppointmentHttp()(appointmentService, doctorService, jwtService, client, eventBus, networkConfig)
    })
}

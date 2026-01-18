package factory

import cats.effect.IO
import cats.effect.Resource
import config.objects.NetworkConfig
import config.CORS.MainCorsPolicy
import doobie.Transactor
import http.{AppointmentHttp, DoctorHttp, OfficeHttp}
import impl._
import nats.EventBus
import org.http4s.HttpRoutes
import service._
import utils.JwtService

final case class AppointmentFactory(
  officeService: OfficeService,
  doctorService: DoctorService,
  serviceService: ServiceService,
  appointmentService: AppointmentService,
  officeRoutes: HttpRoutes[IO],
  doctorRoutes: HttpRoutes[IO],
  appointmentRoutes: HttpRoutes[IO]
)

object AppointmentFactory {

  def apply()(implicit
    t: Transactor[IO],
    jwtService: JwtService,
    eventBus: EventBus,
    networkConfig: NetworkConfig
  ): Resource[IO, AppointmentFactory] =
    Resource.eval(IO {
      val officeRepo = OfficeRepoImpl()
      val doctorRepo = DoctorRepoImpl()
      val scheduleRepo = ScheduleRepoImpl()
      val serviceRepo = ServiceRepoImpl()
      val appointmentRepo = AppointmentRepoImpl()

      val officeService = OfficeService(officeRepo)
      val doctorService = DoctorService(doctorRepo, scheduleRepo)
      val serviceService = ServiceService(serviceRepo)
      val appointmentService = AppointmentService(appointmentRepo, doctorRepo, scheduleRepo, serviceRepo)

      val officeHttp = OfficeHttp(officeService, serviceService)
      val doctorHttp = DoctorHttp(doctorService)
      val appointmentHttp = AppointmentHttp(appointmentService, doctorService)

      val officeRoutes = MainCorsPolicy(officeHttp.routes())
      val doctorRoutes = MainCorsPolicy(doctorHttp.routes())
      val appointmentRoutes = MainCorsPolicy(appointmentHttp.routes())

      AppointmentFactory(
        officeService,
        doctorService,
        serviceService,
        appointmentService,
        officeRoutes,
        doctorRoutes,
        appointmentRoutes
      )
    })
}
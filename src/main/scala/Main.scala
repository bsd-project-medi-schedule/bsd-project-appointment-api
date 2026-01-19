import cats.effect.*
import cats.implicits.toSemigroupKOps
import cats.syntax.all.*
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import config.objects.AuthConfig
import config.objects.NatsConfig
import config.objects.NetworkConfig
import config.AppConfig
import config.CORS.MainCorsPolicy
import config.ConfigUtils
import config.Logging
import db.DbContext
import db.FlywayMigratorApp
import doobie.Transactor
import factory.AppointmentFactory
import factory.DoctorFactory
import factory.OfficeFactory
import fs2.Stream
import impl.AppointmentRepoImpl
import impl.DoctorRepoImpl
import impl.OfficeRepoImpl
import impl.ScheduleRepoImpl
import impl.ServiceRepoImpl
import impl.UserRepoImpl
import nats.EventBus
import nats.EventHandler
import nats.EventProcessor
import nats.NatsClient
import natstools.handlers.UserCreatedHandler
import natstools.handlers.UserDeleteHandler
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.HttpRoutes
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import service.AppointmentService
import service.DoctorService
import service.OfficeService
import service.ReminderService
import service.ServiceService
import utils.JwtService

object Main extends IOApp.Simple with Logging {

  private def eventBusResource(cfg: NatsConfig): Resource[IO, EventBus] =
    NatsClient.resource(cfg).map(client => EventBus.fromNats(client, cfg))

  private def startEventProcessor(handlers: Seq[EventHandler])(implicit
    eventBus: EventBus
  ): Resource[IO, Unit] =
    for {
      processor <- Resource.eval(EventProcessor.create(eventBus))
      handlersIO = IO.parSequence(handlers.map(processor.register))
      _ <- Resource.eval(handlersIO)
      _ <- Resource.make(processor.run.compile.drain.start)(_.cancel).void
    } yield ()

  private def runPeriodicTask(name: String, task: IO[Unit], interval: FiniteDuration): IO[Unit] =
    Stream.awakeEvery[IO](interval)
      .evalMap(_ => task.handleErrorWith(e => logger.error(e)(s"TTL check failed for task $name")))
      .compile
      .drain

  private def startServer(routes: HttpRoutes[IO])(implicit
    networkConfig: NetworkConfig
  ): Resource[IO, Unit] = {
    val host = Host.fromString(networkConfig.appHost).getOrElse(Host.fromString("0.0.0.0").get)
    val port = Port.fromInt(networkConfig.appPort).getOrElse(Port.fromInt(7000).get)

    EmberServerBuilder
      .default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(routes.orNotFound)
      .build
      .void
  }

  private def buildClient(): Resource[IO, Client[IO]] =
    EmberClientBuilder
      .default[IO]
      .build

  private def buildApp(cfg: AppConfig): Resource[IO, (ReminderService, Unit)] =
    (for {
      eventBus <- eventBusResource(cfg.natsConfig)
      implicit0(eb: EventBus) = eventBus

      dbTransactor <- DbContext(cfg.dbConnectionConfig)
      implicit0(xa: Transactor[IO]) = dbTransactor.transactor

      emberClient <- buildClient()
      implicit0(c: Client[IO]) = emberClient

      implicit0(authConfig: AuthConfig) = cfg.authConfig
      implicit0(networkConfig: NetworkConfig) = cfg.networkConfig

      appointmentRepo = AppointmentRepoImpl()
      doctorRepo = DoctorRepoImpl()
      officeRepo = OfficeRepoImpl()
      scheduleRepo = ScheduleRepoImpl()
      serviceRepo = ServiceRepoImpl()
      userRepo = UserRepoImpl()

      appointmentService =
        AppointmentService(appointmentRepo, doctorRepo, scheduleRepo, serviceRepo)
      doctorService = DoctorService(doctorRepo, scheduleRepo)
      officeService = OfficeService(officeRepo)
      reminderService = ReminderService(appointmentService)
      serviceService = ServiceService(serviceRepo)
      jwtService = JwtService()

      appointmentHttp <- AppointmentFactory(appointmentService, doctorService, jwtService)
      doctorHttp      <- DoctorFactory(doctorService, jwtService)
      officeHttp      <- OfficeFactory(officeService, serviceService, jwtService)

      allRoutes = Seq(
        appointmentHttp.routes(),
        doctorHttp.routes(),
        officeHttp.routes()
      ).reduce(_ <+> _)

      mainCorsRoutes = MainCorsPolicy(allRoutes)

      userCreatedHandler = UserCreatedHandler(userRepo)
      userDeleteHandler = UserDeleteHandler(userRepo)

      natsHandlers = Seq(
        userCreatedHandler,
        userDeleteHandler
      )

    } yield (
      startServer(mainCorsRoutes).map(_ => reminderService),
      startEventProcessor(natsHandlers)
    ).parTupled).flatten

  override def run: IO[Unit] =
    for {
      _ <- logger.info("=== Running the Migrations ===")
      _ <- FlywayMigratorApp.migrate()
      _ <- logger.info("Migrations completed")

      _   <- logger.info("Starting main server")
      cfg <- ConfigUtils.loadAndParse[AppConfig]("application.conf", "application")
      _   <- logger.info("Config loaded")
      _   <- logger.info("")

      _ <- logger.info("Starting server")
      _ <- logger.info("")

      _ <- logger.info("=== NATS JetStream Event System Starting ===")
      _ <-
        logger.info(s"Connected to: nats://${cfg.natsConfig.natsHost}:${cfg.natsConfig.natsPort}")
      _ <- logger.info(
        s"Stream '${cfg.natsConfig.streamName}' configured. Handlers registered. Starting event processing..."
      )
      _ <- logger.info("")

      _ <- buildApp(cfg).use {
        case (reminderService, _) =>
          val reminderTask = runPeriodicTask(
            "Appointment reminders",
            reminderService.sendPendingReminders(),
            1.hour
          )

          List(
            IO.never,
            reminderTask
          ).parSequence_.void
      }
    } yield ()

}

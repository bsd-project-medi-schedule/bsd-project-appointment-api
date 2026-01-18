import cats.effect.*
import cats.implicits.toSemigroupKOps
import config.objects.NetworkConfig
import config.{AppConfig, ConfigUtils, Logging}
import db.{DbContext, FlywayMigratorApp}
import doobie.Transactor
import factory.{AppointmentFactory, AuthFactory}
import fs2.Stream
import nats.{EventBus, EventProcessor, NatsClient}
import org.http4s.blaze.server.BlazeServerBuilder
import service.ReminderService
import utils.JwtService

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps

object Main extends IOApp.Simple with Logging {
  private def runPeriodicTask(name: String, task: IO[Unit], interval: FiniteDuration): IO[Unit] =
    Stream.awakeEvery[IO](interval)
      .evalMap(_ => task.handleErrorWith(e => logger.error(e)(s"Periodic task failed: $name")))
      .compile
      .drain

  private def startServer(cfg: AppConfig)(implicit eventBus: EventBus): Resource[IO, ReminderService] =
    for {
      dbTransactor <- DbContext(cfg.dbConnectionConfig)

      implicit0(t: Transactor[IO])            = dbTransactor.transactor
      implicit0(networkConfig: NetworkConfig) = cfg.networkConfig
      implicit0(jwtService: JwtService)       = JwtService(cfg.authConfig)

      af <- AuthFactory()
      apf <- AppointmentFactory()

      reminderService = ReminderService(apf.appointmentService)

      allRoutes = Seq(
        af.authRoutes,
        apf.officeRoutes,
        apf.doctorRoutes,
        apf.appointmentRoutes
      )

      routes = allRoutes.reduce(_ <+> _).orNotFound

      _ <- BlazeServerBuilder[IO]
        .bindHttp(cfg.networkConfig.appPort, cfg.networkConfig.appHost)
        .withHttpApp(routes)
        .resource
    } yield reminderService

  private def eventBusResource(cfg: AppConfig): Resource[IO, EventBus] =
    for {
      client <- NatsClient.resource(cfg.natsConfig)
    } yield EventBus.fromNats(client, cfg.natsConfig)

  private def startEventProcessor(eventBus: EventBus): Resource[IO, Unit] =
    for {
      processor <- Resource.eval(EventProcessor.create(eventBus))
      _         <- Resource.make(processor.run.compile.drain.start)(_.cancel)
    } yield ()

  override def run: IO[Unit] =
    for {
      _ <- IO.println("=== Running the Migrations ===")
      _ <- FlywayMigratorApp.migrate()
      _ <- IO.println("Migrations completed")

      _   <- IO.println("Starting Appointment API server")
      cfg <- ConfigUtils.loadAndParse[AppConfig]("application.conf", "application")
      _   <- IO.println("Config loaded")
      _   <- IO.println("")

      _ <- IO.println("Starting server")
      _ <- IO.println("")

      _ <- IO.println("=== NATS JetStream Event System Starting ===")
      _ <- IO.println(s"Connected to: nats://${cfg.natsConfig.natsHost}:${cfg.natsConfig.natsPort}")
      _ <- IO.println(s"Stream '${cfg.natsConfig.streamName}' configured. Starting event processing...")
      _ <- IO.println("")

      _ <- eventBusResource(cfg).use { implicit eventBus =>
        (startServer(cfg), startEventProcessor(eventBus)).parTupled.use { case (reminderService, _) =>
          val reminderTask = runPeriodicTask("Appointment reminders", reminderService.sendPendingReminders(), 1.hour)

          List(
            IO.never,
            reminderTask
          ).parSequence_.void
        }
      }
    } yield ()

}
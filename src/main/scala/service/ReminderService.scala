package service

import cats.effect.IO
import nats.EventBus
import nats.NatsEvent
import natstools.events.AppointmentEmailEvent
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

final case class ReminderService(
  appointmentService: AppointmentService
)(implicit eventBus: EventBus) {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  def sendPendingReminders(hoursAhead: Int = 24): IO[Unit] = {
    appointmentService.getPendingReminders(hoursAhead).value.flatMap {
      case Left(err) =>
        logger.error(s"Failed to get pending reminders: ${err.toString}")

      case Right(appointments) =>
        appointments.traverse_ { appointment =>
          val reminderEvent = NatsEvent.create[AppointmentEmailEvent]((id, ts) =>
            AppointmentEmailEvent(id, ts, appointment.patientEmail.getOrElse(""), AppointmentEmailEvent.PURPOSE_REMINDER,
              Map(
                "name" -> appointment.patientName.getOrElse("Patient"),
                "appointmentId" -> appointment.id.map(_.toString).getOrElse(""),
                "appointmentTime" -> appointment.appointmentTime.toString,
                "doctorName" -> appointment.doctorName.getOrElse(""),
                "serviceName" -> appointment.serviceName.getOrElse(""),
                "officeName" -> appointment.officeName.getOrElse("")
              ))
          )

          for {
            _ <- eventBus.publish(reminderEvent)
            _ <- appointment.id match {
              case Some(id) => appointmentService.markReminderSent(id).value.void
              case None => IO.unit
            }
            _ <- logger.info(s"Sent reminder for appointment ${appointment.id}")
          } yield ()
        }
    }
  }

  private implicit class ListOps[A](list: List[A]) {
    def traverse_[B](f: A => IO[B]): IO[Unit] =
      list.foldLeft(IO.unit)((acc, a) => acc >> f(a).void)
  }
}
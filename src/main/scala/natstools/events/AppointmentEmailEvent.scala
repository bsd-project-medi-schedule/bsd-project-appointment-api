package natstools.events

import io.circe._
import io.circe.generic.semiauto._
import java.time.Instant
import java.util.UUID
import nats.NatsEvent

case class AppointmentEmailEvent(
  eventId: UUID,
  timestamp: Instant,
  email: String,
  purpose: String,
  metadata: Map[String, String]
) extends NatsEvent {
  val eventType: String = AppointmentEmailEvent.EVENT_TYPE
  protected def baseEncoder: Encoder[this.type] =
    AppointmentEmailEvent.codec.asInstanceOf[Encoder[this.type]]
}

object AppointmentEmailEvent {
  private val EVENT_TYPE = "message.email"
  implicit val codec: Codec[AppointmentEmailEvent] = deriveCodec
  NatsEvent.register(EVENT_TYPE, codec)

  val PURPOSE_CONFIRM = "email.appointment.confirm"
  val PURPOSE_REMINDER = "email.appointment.reminder"
  val PURPOSE_CANCELLED = "email.appointment.cancelled"
  val PURPOSE_DOCTOR_WELCOME = "email.doctor.welcome"
}
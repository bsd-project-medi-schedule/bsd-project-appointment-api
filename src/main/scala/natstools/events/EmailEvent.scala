package natstools.events

import io.circe.*
import io.circe.generic.semiauto.*
import java.time.Instant
import java.util.UUID
import nats.NatsEvent

case class EmailEvent(
  eventId: UUID,
  timestamp: Instant,
  email: String,
  purpose: String,
  metadata: Map[String, String]
) extends NatsEvent {
  val eventType: String = EmailEvent.EVENT_TYPE

  protected def baseEncoder: Encoder[this.type] =
    EmailEvent.codec.asInstanceOf[Encoder[this.type]]
}

object EmailEvent {
  private val EVENT_TYPE = "message.email"
  implicit val codec: Codec[EmailEvent] = deriveCodec
  NatsEvent.register(EVENT_TYPE, codec)

  val PURPOSE_CONFIRM = "email.appointment.confirm"
  val PURPOSE_REMINDER = "email.appointment.reminder"
  val PURPOSE_CANCELLED = "email.appointment.cancelled"
  val PURPOSE_DOCTOR_WELCOME = "email.doctor.welcome"
}
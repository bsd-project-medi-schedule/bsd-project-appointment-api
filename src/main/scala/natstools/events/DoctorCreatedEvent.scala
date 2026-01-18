package natstools.events

import io.circe._
import io.circe.generic.semiauto._
import java.time.Instant
import java.util.UUID
import nats.NatsEvent

case class DoctorCreatedEvent(
  eventId: UUID,
  timestamp: Instant,
  email: String,
  password: String,
  firstName: String,
  lastName: String
) extends NatsEvent {
  val eventType: String = DoctorCreatedEvent.EVENT_TYPE
  protected def baseEncoder: Encoder[this.type] =
    DoctorCreatedEvent.codec.asInstanceOf[Encoder[this.type]]
}

object DoctorCreatedEvent {
  private val EVENT_TYPE = "user.doctor.created"
  implicit val codec: Codec[DoctorCreatedEvent] = deriveCodec
  NatsEvent.register(EVENT_TYPE, codec)
}
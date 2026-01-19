package natstools.events

import io.circe.*
import io.circe.generic.semiauto.*
import nats.NatsEvent

import java.time.Instant
import java.util.UUID

case class UserCreatedEvent(
  eventId: UUID,
  timestamp: Instant,

  userId: UUID,
  email: String,
  role: Int
) extends NatsEvent {
  val eventType: String = UserCreatedEvent.EVENT_TYPE
  protected def baseEncoder: Encoder[this.type] =
    UserCreatedEvent.codec.asInstanceOf[Encoder[this.type]]
}

object UserCreatedEvent {
  val EVENT_TYPE = "user.created"
  implicit val codec: Codec[UserCreatedEvent] = deriveCodec
  NatsEvent.register(EVENT_TYPE, codec)
}

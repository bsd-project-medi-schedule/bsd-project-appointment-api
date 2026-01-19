package natstools.events

import io.circe.*
import io.circe.generic.semiauto.*
import nats.NatsEvent

import java.time.Instant
import java.util.UUID

case class LoginSessionEvent(
  eventId: UUID,
  timestamp: Instant,

  oldToken: String,
  newToken: String
) extends NatsEvent {
  val eventType: String = LoginSessionEvent.EVENT_TYPE
  protected def baseEncoder: Encoder[this.type] =
    LoginSessionEvent.codec.asInstanceOf[Encoder[this.type]]
}

object LoginSessionEvent {
  val EVENT_TYPE = "user.token"
  implicit val codec: Codec[LoginSessionEvent] = deriveCodec
  NatsEvent.register(EVENT_TYPE, codec)
}

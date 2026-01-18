package natstools.events

import io.circe._
import io.circe.generic.semiauto._
import java.time.Instant
import java.util.UUID
import nats.NatsEvent

case class RefreshTokenEvent(
  eventId: UUID,
  timestamp: Instant,
  userId: UUID,
  newRefreshToken: String
) extends NatsEvent {
  val eventType: String = RefreshTokenEvent.EVENT_TYPE
  protected def baseEncoder: Encoder[this.type] =
    RefreshTokenEvent.codec.asInstanceOf[Encoder[this.type]]
}

object RefreshTokenEvent {
  private val EVENT_TYPE = "user.refresh_token"
  implicit val codec: Codec[RefreshTokenEvent] = deriveCodec
  NatsEvent.register(EVENT_TYPE, codec)
}
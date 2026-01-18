package nats

import io.circe.*

import java.time.Instant
import java.util.UUID

trait NatsEvent {
  def eventId: UUID
  def timestamp: Instant
  def eventType: String

  // Each event knows how to encode itself (derived codec + type field)
  protected def baseEncoder: Encoder[this.type]
  private final def toJson: Json =
    baseEncoder(this).mapObject(_.add("type", Json.fromString(eventType)))
}

object NatsEvent {

  // Helper to create events with common fields pre-filled
  def create[E <: NatsEvent](f: (UUID, Instant) => E): E =
    f(UUID.randomUUID(), Instant.now())

  // Registration: maps event type string -> decoder
  private var decoderRegistry: Map[String, Decoder[? <: NatsEvent]] = Map.empty

  def register[E <: NatsEvent](eventType: String, decoder: Decoder[E]): Decoder[E] = {
    decoderRegistry = decoderRegistry + (eventType -> decoder)
    decoder
  }

  implicit val eventEncoder: Encoder[NatsEvent] = Encoder.instance(_.toJson)

  implicit val eventDecoder: Decoder[NatsEvent] = Decoder.instance { cursor =>
    cursor.get[String]("type").flatMap { eventType =>
      decoderRegistry.get(eventType) match {
        case Some(decoder) => decoder.tryDecode(cursor)
        case None => Left(DecodingFailure(s"Unknown event type: $eventType", cursor.history))
      }
    }
  }
}

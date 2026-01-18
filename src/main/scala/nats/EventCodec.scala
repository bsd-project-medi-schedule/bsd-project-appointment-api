package nats

import io.circe.parser.*
import io.circe.syntax.*
import NatsEvent.{eventEncoder, eventDecoder}

object EventCodec {

  def encode(event: NatsEvent): String =
    event.asJson.noSpaces

  def decode(json: String): Either[String, NatsEvent] =
    parse(json)
      .flatMap(_.as[NatsEvent])
      .left.map(_.getMessage)
}
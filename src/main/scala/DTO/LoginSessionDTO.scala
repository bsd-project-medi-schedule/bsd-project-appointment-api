package DTO

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import java.time.Instant
import java.util.UUID

case class LoginSessionDTO(
  id: UUID,
  token: String,
  expiresAt: Option[Instant],
)

object LoginSessionDTO {
  implicit val codec: Codec[LoginSessionDTO] = deriveCodec
}

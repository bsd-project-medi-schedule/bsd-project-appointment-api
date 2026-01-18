package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import java.time.Instant
import java.util.UUID

case class OfficeDTO(
  id: Option[UUID] = None,
  name: String,
  description: Option[String] = None,
  specialization: Option[String] = None,
  location: String,
  phone: Option[String] = None,
  email: Option[String] = None,
  createdAt: Option[Instant] = None
)

object OfficeDTO {
  implicit val codec: Codec[OfficeDTO] = deriveCodec
}
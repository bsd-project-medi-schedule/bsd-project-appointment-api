package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec

case class GenericMessageDTO(
  message: String,
)

object GenericMessageDTO {
  implicit val codec: Codec[GenericMessageDTO] = deriveCodec
}

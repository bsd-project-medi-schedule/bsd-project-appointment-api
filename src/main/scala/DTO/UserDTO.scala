package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import java.util.UUID

case class UserDTO(
  id: UUID,
  email: String,
  role: Int,
)

object UserDTO {
  implicit val codec: Codec[UserDTO] = deriveCodec
}
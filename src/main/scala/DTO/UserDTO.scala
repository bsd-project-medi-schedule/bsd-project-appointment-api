package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import java.util.UUID

case class UserDTO(
  id: UUID,
  email: String,
  role: Option[Int] = None,
  firstName: Option[String] = None,
  lastName: Option[String] = None
)

object UserDTO {
  implicit val codec: Codec[UserDTO] = deriveCodec
}
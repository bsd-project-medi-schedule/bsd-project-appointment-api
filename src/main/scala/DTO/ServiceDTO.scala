package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import java.util.UUID

case class ServiceTypeDTO(
  id: Option[UUID] = None,
  name: String,
  description: Option[String] = None
)

object ServiceTypeDTO {
  implicit val codec: Codec[ServiceTypeDTO] = deriveCodec
}

case class ServiceDTO(
  id: Option[UUID] = None,
  typeId: UUID,
  officeId: UUID,
  durationInMinutes: Int,
  price: BigDecimal,
  isActive: Option[Boolean] = Some(true),
  notes: Option[String] = None,
  typeName: Option[String] = None
)

object ServiceDTO {
  implicit val codec: Codec[ServiceDTO] = deriveCodec
}

case class ServiceCreateDTO(
  typeName: String,
  typeDescription: Option[String] = None,
  durationInMinutes: Int,
  price: BigDecimal,
  notes: Option[String] = None
)

object ServiceCreateDTO {
  implicit val codec: Codec[ServiceCreateDTO] = deriveCodec
}
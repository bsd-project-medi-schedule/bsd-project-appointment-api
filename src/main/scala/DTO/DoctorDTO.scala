package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import java.time.Instant
import java.util.UUID

case class DoctorDTO(
  id: Option[UUID] = None,
  userId: UUID,
  officeId: UUID,
  fieldOfAction: String,
  firstName: String,
  lastName: String,
  email: String,
  phone: Option[String] = None,
  isActive: Option[Boolean] = Some(true),
  createdAt: Option[Instant] = None
)

object DoctorDTO {
  implicit val codec: Codec[DoctorDTO] = deriveCodec
}

case class DoctorImportDTO(
  fieldOfAction: String,
  firstName: String,
  lastName: String,
  email: String,
  phone: Option[String] = None,
  password: String
)

object DoctorImportDTO {
  implicit val codec: Codec[DoctorImportDTO] = deriveCodec
}
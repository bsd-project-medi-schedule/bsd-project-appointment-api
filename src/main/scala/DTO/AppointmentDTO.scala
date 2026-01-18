package DTO

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import java.time.Instant
import java.util.UUID

case class AppointmentDTO(
  id: Option[UUID] = None,
  patientId: UUID,
  doctorId: UUID,
  officeId: UUID,
  serviceId: UUID,
  appointmentTime: Instant,
  endTime: Option[Instant] = None,
  status: String = "PENDING",
  notes: Option[String] = None,
  confirmationToken: Option[String] = None,
  confirmedAt: Option[Instant] = None,
  reminderSent: Option[Boolean] = Some(false),
  createdAt: Option[Instant] = None,
  updatedAt: Option[Instant] = None,
  doctorName: Option[String] = None,
  serviceName: Option[String] = None,
  officeName: Option[String] = None,
  patientName: Option[String] = None,
  patientEmail: Option[String] = None
)

object AppointmentDTO {
  implicit val codec: Codec[AppointmentDTO] = deriveCodec
}

case class BookAppointmentDTO(
  officeId: UUID,
  serviceId: UUID,
  appointmentTime: Instant,
  notes: Option[String] = None
)

object BookAppointmentDTO {
  implicit val codec: Codec[BookAppointmentDTO] = deriveCodec
}

case class AppointmentSlotDTO(
  time: Instant,
  doctorId: UUID,
  doctorName: String,
  isAvailable: Boolean
)

object AppointmentSlotDTO {
  implicit val codec: Codec[AppointmentSlotDTO] = deriveCodec
}
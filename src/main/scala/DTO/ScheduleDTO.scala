package DTO

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

case class ScheduleDTO(
  id: Option[UUID] = None,
  doctorId: UUID,
  dayOfWeek: Int,
  startTime: LocalTime,
  endTime: LocalTime,
  slotDurationMin: Int = 30,
  isActive: Option[Boolean] = Some(true)
)

object ScheduleDTO {
  implicit val localTimeEncoder: Encoder[LocalTime] = Encoder.encodeString.contramap(_.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
  implicit val localTimeDecoder: Decoder[LocalTime] = Decoder.decodeString.map(LocalTime.parse(_, DateTimeFormatter.ofPattern("HH:mm:ss")))
  implicit val codec: Codec[ScheduleDTO] = deriveCodec
}

case class ScheduleCreateDTO(
  dayOfWeek: Int,
  startTime: String,
  endTime: String,
  slotDurationMin: Int = 30
)

object ScheduleCreateDTO {
  implicit val codec: Codec[ScheduleCreateDTO] = deriveCodec
}
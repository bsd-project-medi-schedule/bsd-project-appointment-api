package repo

import DTO.OfficeDTO
import cats.effect.IO
import java.util.UUID

trait OfficeRepo {
  def createAndGetId(office: OfficeDTO): IO[Option[UUID]]
  def readById(id: UUID): IO[Option[OfficeDTO]]
  def readAll(offset: Int, size: Int): IO[List[OfficeDTO]]
  def update(id: UUID, office: OfficeDTO): IO[Int]
  def delete(id: UUID): IO[Int]
}
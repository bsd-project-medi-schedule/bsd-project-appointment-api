package repo

import DTO.{ServiceDTO, ServiceTypeDTO}
import cats.effect.IO
import java.util.UUID

trait ServiceRepo {
  def createTypeAndGetId(serviceType: ServiceTypeDTO): IO[Option[UUID]]
  def getTypeByName(name: String): IO[Option[ServiceTypeDTO]]
  def getOrCreateType(name: String, description: Option[String]): IO[UUID]

  def createAndGetId(service: ServiceDTO): IO[Option[UUID]]
  def readById(id: UUID): IO[Option[ServiceDTO]]
  def readByOffice(officeId: UUID, offset: Int, size: Int): IO[List[ServiceDTO]]
  def readAll(offset: Int, size: Int): IO[List[ServiceDTO]]
  def update(id: UUID, service: ServiceDTO): IO[Int]
  def delete(id: UUID): IO[Int]
}
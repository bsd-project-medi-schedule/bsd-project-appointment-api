package repo

import DTO.DoctorDTO
import cats.effect.IO
import java.util.UUID

trait DoctorRepo {
  def createAndGetId(doctor: DoctorDTO): IO[Option[UUID]]
  def readById(id: UUID): IO[Option[DoctorDTO]]
  def readByUserId(userId: UUID): IO[Option[DoctorDTO]]
  def readByOffice(officeId: UUID, offset: Int, size: Int): IO[List[DoctorDTO]]
  def readByFieldOfAction(field: String, officeId: UUID): IO[List[DoctorDTO]]
  def readAll(offset: Int, size: Int): IO[List[DoctorDTO]]
  def update(id: UUID, doctor: DoctorDTO): IO[Int]
  def delete(id: UUID): IO[Int]
  def addServiceToDoctor(doctorId: UUID, serviceId: UUID): IO[Int]
  def removeServiceFromDoctor(doctorId: UUID, serviceId: UUID): IO[Int]
  def getDoctorServices(doctorId: UUID): IO[List[UUID]]
  def getDoctorsForService(serviceId: UUID, officeId: UUID): IO[List[DoctorDTO]]
}
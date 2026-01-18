package service

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.either._
import java.util.UUID
import repo.ServiceRepo
import DTO.{ServiceDTO, ServiceCreateDTO}
import error.ServiceError

final case class ServiceService(serviceRepo: ServiceRepo) {

  def createService(officeId: UUID, serviceCreate: ServiceCreateDTO): EitherT[IO, ServiceError, UUID] = {
    for {
      _ <- EitherT.cond[IO](
        serviceCreate.typeName.nonEmpty,
        (),
        ServiceError.BadRequest("Service type name is required"): ServiceError
      )
      _ <- EitherT.cond[IO](
        serviceCreate.durationInMinutes > 0,
        (),
        ServiceError.BadRequest("Duration must be positive"): ServiceError
      )
      _ <- EitherT.cond[IO](
        serviceCreate.price >= 0,
        (),
        ServiceError.BadRequest("Price cannot be negative"): ServiceError
      )
      typeId <- EitherT.liftF(serviceRepo.getOrCreateType(serviceCreate.typeName, serviceCreate.typeDescription))
      service = ServiceDTO(
        typeId = typeId,
        officeId = officeId,
        durationInMinutes = serviceCreate.durationInMinutes,
        price = serviceCreate.price,
        notes = serviceCreate.notes
      )
      serviceId <- EitherT.fromOptionF(
        serviceRepo.createAndGetId(service),
        ServiceError.InternalError("Could not create service"): ServiceError
      )
    } yield serviceId
  }

  def readService(id: UUID): EitherT[IO, ServiceError, ServiceDTO] =
    EitherT.fromOptionF(serviceRepo.readById(id), ServiceError.NotFound("Service not found"): ServiceError)

  def readServicesByOffice(officeId: UUID, offset: Int, size: Int): EitherT[IO, ServiceError, List[ServiceDTO]] =
    EitherT(serviceRepo.readByOffice(officeId, offset, size).map(_.asRight[ServiceError]))

  def readServices(offset: Int, size: Int): EitherT[IO, ServiceError, List[ServiceDTO]] =
    EitherT(serviceRepo.readAll(offset, size).map(_.asRight[ServiceError]))

  def updateService(id: UUID, service: ServiceDTO): EitherT[IO, ServiceError, Unit] =
    EitherT(serviceRepo.update(id, service).map {
      case 0 => Left(ServiceError.NotFound("Service not found"))
      case _ => Right(())
    })

  def deleteService(id: UUID): EitherT[IO, ServiceError, Unit] =
    EitherT(serviceRepo.delete(id).map {
      case 0 => Left(ServiceError.NotFound("Service not found"))
      case _ => Right(())
    })
}
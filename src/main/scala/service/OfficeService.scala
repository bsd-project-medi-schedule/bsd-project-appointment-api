package service

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.either._
import java.util.UUID
import repo.OfficeRepo
import DTO.OfficeDTO
import error.ServiceError

final case class OfficeService(officeRepo: OfficeRepo) {

  def createOffice(office: OfficeDTO): EitherT[IO, ServiceError, UUID] = {
    for {
      _ <- EitherT.cond[IO](
        office.name.nonEmpty,
        (),
        ServiceError.BadRequest("Office name is required"): ServiceError
      )
      _ <- EitherT.cond[IO](
        office.location.nonEmpty,
        (),
        ServiceError.BadRequest("Office location is required"): ServiceError
      )
      officeId <- EitherT.fromOptionF(
        officeRepo.createAndGetId(office),
        ServiceError.InternalError("Could not create office"): ServiceError
      )
    } yield officeId
  }

  def readOffice(id: UUID): EitherT[IO, ServiceError, OfficeDTO] =
    EitherT.fromOptionF(officeRepo.readById(id), ServiceError.NotFound("Office not found"): ServiceError)

  def readOffices(offset: Int, size: Int): EitherT[IO, ServiceError, List[OfficeDTO]] =
    EitherT(officeRepo.readAll(offset, size).map(_.asRight[ServiceError]))

  def updateOffice(id: UUID, office: OfficeDTO): EitherT[IO, ServiceError, Unit] =
    EitherT(officeRepo.update(id, office).map {
      case 0 => Left(ServiceError.NotFound("Office not found"))
      case _ => Right(())
    })

  def deleteOffice(id: UUID): EitherT[IO, ServiceError, Unit] =
    EitherT(officeRepo.delete(id).map {
      case 0 => Left(ServiceError.NotFound("Office not found"))
      case _ => Right(())
    })
}
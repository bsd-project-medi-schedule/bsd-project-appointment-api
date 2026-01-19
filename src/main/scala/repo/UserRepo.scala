package repo

import DTO.UserDTO
import cats.effect.IO

import java.util.UUID

trait UserRepo {
  def createAndGetId(user: UserDTO): IO[Option[UUID]]

  def deleteById(id: UUID): IO[Int]
}
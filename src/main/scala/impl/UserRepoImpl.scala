package impl

import DTO.UserDTO
import cats.effect.IO
import doobie.Transactor
import doobie.implicits.*
import doobie.postgres.implicits.*
import repo.UserRepo

import java.util.UUID

final case class UserRepoImpl()(implicit t: Transactor[IO]) extends UserRepo {

  override def createAndGetId(user: UserDTO): IO[Option[UUID]] =
    sql"""
     INSERT INTO users (id, email, role)
     VALUES (${user.id}, ${user.email}, ${user.role})
     RETURNING id
     """.query[UUID].option.transact(t)

  override def deleteById(id: UUID): IO[Int] =
    sql"""
     DELETE FROM users where id = $id
     """.update.run.transact(t)

}
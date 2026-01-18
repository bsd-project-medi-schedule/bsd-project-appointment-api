package impl

import cats.effect.IO
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.Transactor
import java.util.UUID
import repo.OfficeRepo
import DTO.OfficeDTO

final case class OfficeRepoImpl()(implicit t: Transactor[IO]) extends OfficeRepo {

  override def createAndGetId(office: OfficeDTO): IO[Option[UUID]] =
    sql"""
      INSERT INTO office (name, description, specialization, location, phone, email)
      VALUES (${office.name}, ${office.description}, ${office.specialization},
              ${office.location}, ${office.phone}, ${office.email})
      RETURNING id
    """.query[UUID].option.transact(t)

  override def readById(id: UUID): IO[Option[OfficeDTO]] =
    sql"""
      SELECT id, name, description, specialization, location, phone, email, created_at
      FROM office
      WHERE id = $id
    """.query[OfficeDTO].option.transact(t)

  override def readAll(offset: Int, size: Int): IO[List[OfficeDTO]] =
    sql"""
      SELECT id, name, description, specialization, location, phone, email, created_at
      FROM office
      ORDER BY name
      LIMIT $size OFFSET $offset
    """.query[OfficeDTO].to[List].transact(t)

  override def update(id: UUID, office: OfficeDTO): IO[Int] =
    sql"""
      UPDATE office
      SET name = ${office.name},
          description = ${office.description},
          specialization = ${office.specialization},
          location = ${office.location},
          phone = ${office.phone},
          email = ${office.email}
      WHERE id = $id
    """.update.run.transact(t)

  override def delete(id: UUID): IO[Int] =
    sql"DELETE FROM office WHERE id = $id".update.run.transact(t)
}
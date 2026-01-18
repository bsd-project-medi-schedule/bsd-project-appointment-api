package impl

import cats.effect.IO
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.Transactor
import java.util.UUID
import repo.ServiceRepo
import DTO.{ServiceDTO, ServiceTypeDTO}

final case class ServiceRepoImpl()(implicit t: Transactor[IO]) extends ServiceRepo {

  override def createTypeAndGetId(serviceType: ServiceTypeDTO): IO[Option[UUID]] =
    sql"""
      INSERT INTO service_type (name, description)
      VALUES (${serviceType.name}, ${serviceType.description})
      ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description
      RETURNING id
    """.query[UUID].option.transact(t)

  override def getTypeByName(name: String): IO[Option[ServiceTypeDTO]] =
    sql"""
      SELECT id, name, description
      FROM service_type
      WHERE name = $name
    """.query[ServiceTypeDTO].option.transact(t)

  override def getOrCreateType(name: String, description: Option[String]): IO[UUID] =
    sql"""
      INSERT INTO service_type (name, description)
      VALUES ($name, $description)
      ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
      RETURNING id
    """.query[UUID].unique.transact(t)

  override def createAndGetId(service: ServiceDTO): IO[Option[UUID]] =
    sql"""
      INSERT INTO service (type_id, office_id, duration_in_minutes, price, is_active, notes)
      VALUES (${service.typeId}, ${service.officeId}, ${service.durationInMinutes},
              ${service.price}, ${service.isActive.getOrElse(true)}, ${service.notes})
      RETURNING id
    """.query[UUID].option.transact(t)

  override def readById(id: UUID): IO[Option[ServiceDTO]] =
    sql"""
      SELECT s.id, s.type_id, s.office_id, s.duration_in_minutes, s.price, s.is_active, s.notes, st.name as type_name
      FROM service s
      JOIN service_type st ON s.type_id = st.id
      WHERE s.id = $id
    """.query[ServiceDTO].option.transact(t)

  override def readByOffice(officeId: UUID, offset: Int, size: Int): IO[List[ServiceDTO]] =
    sql"""
      SELECT s.id, s.type_id, s.office_id, s.duration_in_minutes, s.price, s.is_active, s.notes, st.name as type_name
      FROM service s
      JOIN service_type st ON s.type_id = st.id
      WHERE s.office_id = $officeId AND s.is_active = true
      ORDER BY st.name
      LIMIT $size OFFSET $offset
    """.query[ServiceDTO].to[List].transact(t)

  override def readAll(offset: Int, size: Int): IO[List[ServiceDTO]] =
    sql"""
      SELECT s.id, s.type_id, s.office_id, s.duration_in_minutes, s.price, s.is_active, s.notes, st.name as type_name
      FROM service s
      JOIN service_type st ON s.type_id = st.id
      WHERE s.is_active = true
      ORDER BY st.name
      LIMIT $size OFFSET $offset
    """.query[ServiceDTO].to[List].transact(t)

  override def update(id: UUID, service: ServiceDTO): IO[Int] =
    sql"""
      UPDATE service
      SET duration_in_minutes = ${service.durationInMinutes},
          price = ${service.price},
          is_active = ${service.isActive.getOrElse(true)},
          notes = ${service.notes}
      WHERE id = $id
    """.update.run.transact(t)

  override def delete(id: UUID): IO[Int] =
    sql"UPDATE service SET is_active = false WHERE id = $id".update.run.transact(t)
}
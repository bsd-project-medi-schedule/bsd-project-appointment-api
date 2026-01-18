package impl

import cats.effect.IO
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.Transactor
import java.util.UUID
import repo.DoctorRepo
import DTO.DoctorDTO

final case class DoctorRepoImpl()(implicit t: Transactor[IO]) extends DoctorRepo {

  override def createAndGetId(doctor: DoctorDTO): IO[Option[UUID]] =
    sql"""
      INSERT INTO doctor (user_id, office_id, field_of_action, first_name, last_name, email, phone, is_active)
      VALUES (${doctor.userId}, ${doctor.officeId}, ${doctor.fieldOfAction},
              ${doctor.firstName}, ${doctor.lastName}, ${doctor.email}, ${doctor.phone}, ${doctor.isActive.getOrElse(true)})
      RETURNING id
    """.query[UUID].option.transact(t)

  override def readById(id: UUID): IO[Option[DoctorDTO]] =
    sql"""
      SELECT id, user_id, office_id, field_of_action, first_name, last_name, email, phone, is_active, created_at
      FROM doctor
      WHERE id = $id
    """.query[DoctorDTO].option.transact(t)

  override def readByUserId(userId: UUID): IO[Option[DoctorDTO]] =
    sql"""
      SELECT id, user_id, office_id, field_of_action, first_name, last_name, email, phone, is_active, created_at
      FROM doctor
      WHERE user_id = $userId
    """.query[DoctorDTO].option.transact(t)

  override def readByOffice(officeId: UUID, offset: Int, size: Int): IO[List[DoctorDTO]] =
    sql"""
      SELECT id, user_id, office_id, field_of_action, first_name, last_name, email, phone, is_active, created_at
      FROM doctor
      WHERE office_id = $officeId AND is_active = true
      ORDER BY last_name, first_name
      LIMIT $size OFFSET $offset
    """.query[DoctorDTO].to[List].transact(t)

  override def readByFieldOfAction(field: String, officeId: UUID): IO[List[DoctorDTO]] =
    sql"""
      SELECT id, user_id, office_id, field_of_action, first_name, last_name, email, phone, is_active, created_at
      FROM doctor
      WHERE field_of_action = $field AND office_id = $officeId AND is_active = true
      ORDER BY last_name, first_name
    """.query[DoctorDTO].to[List].transact(t)

  override def readAll(offset: Int, size: Int): IO[List[DoctorDTO]] =
    sql"""
      SELECT id, user_id, office_id, field_of_action, first_name, last_name, email, phone, is_active, created_at
      FROM doctor
      WHERE is_active = true
      ORDER BY last_name, first_name
      LIMIT $size OFFSET $offset
    """.query[DoctorDTO].to[List].transact(t)

  override def update(id: UUID, doctor: DoctorDTO): IO[Int] =
    sql"""
      UPDATE doctor
      SET field_of_action = ${doctor.fieldOfAction},
          first_name = ${doctor.firstName},
          last_name = ${doctor.lastName},
          email = ${doctor.email},
          phone = ${doctor.phone},
          is_active = ${doctor.isActive.getOrElse(true)}
      WHERE id = $id
    """.update.run.transact(t)

  override def delete(id: UUID): IO[Int] =
    sql"UPDATE doctor SET is_active = false WHERE id = $id".update.run.transact(t)

  override def addServiceToDoctor(doctorId: UUID, serviceId: UUID): IO[Int] =
    sql"""
      INSERT INTO doctor_service (doctor_id, service_id)
      VALUES ($doctorId, $serviceId)
      ON CONFLICT DO NOTHING
    """.update.run.transact(t)

  override def removeServiceFromDoctor(doctorId: UUID, serviceId: UUID): IO[Int] =
    sql"DELETE FROM doctor_service WHERE doctor_id = $doctorId AND service_id = $serviceId".update.run.transact(t)

  override def getDoctorServices(doctorId: UUID): IO[List[UUID]] =
    sql"SELECT service_id FROM doctor_service WHERE doctor_id = $doctorId".query[UUID].to[List].transact(t)

  override def getDoctorsForService(serviceId: UUID, officeId: UUID): IO[List[DoctorDTO]] =
    sql"""
      SELECT d.id, d.user_id, d.office_id, d.field_of_action, d.first_name, d.last_name, d.email, d.phone, d.is_active, d.created_at
      FROM doctor d
      JOIN doctor_service ds ON d.id = ds.doctor_id
      WHERE ds.service_id = $serviceId AND d.office_id = $officeId AND d.is_active = true
      ORDER BY d.last_name, d.first_name
    """.query[DoctorDTO].to[List].transact(t)
}
package impl

import cats.effect.IO
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.Transactor
import java.time.Instant
import java.util.UUID
import repo.AppointmentRepo
import DTO.AppointmentDTO

final case class AppointmentRepoImpl()(implicit t: Transactor[IO]) extends AppointmentRepo {

  override def createAndGetId(appointment: AppointmentDTO): IO[Option[UUID]] =
    sql"""
      INSERT INTO appointment (patient_id, doctor_id, office_id, service_id, appointment_time, end_time, status, notes, confirmation_token)
      VALUES (${appointment.patientId}, ${appointment.doctorId}, ${appointment.officeId}, ${appointment.serviceId},
              ${appointment.appointmentTime}, ${appointment.endTime}, ${appointment.status}::appointment_status,
              ${appointment.notes}, ${appointment.confirmationToken})
      RETURNING id
    """.query[UUID].option.transact(t)

  override def readById(id: UUID): IO[Option[AppointmentDTO]] =
    sql"""
      SELECT a.id, a.patient_id, a.doctor_id, a.office_id, a.service_id, a.appointment_time, a.end_time,
             a.status::text, a.notes, a.confirmation_token, a.confirmed_at, a.reminder_sent, a.created_at, a.updated_at,
             CONCAT(d.first_name, ' ', d.last_name) as doctor_name,
             st.name as service_name,
             o.name as office_name,
             u.first_name as patient_name,
             u.email as patient_email
      FROM appointment a
      JOIN doctor d ON a.doctor_id = d.id
      JOIN service s ON a.service_id = s.id
      JOIN service_type st ON s.type_id = st.id
      JOIN office o ON a.office_id = o.id
      JOIN users u ON a.patient_id = u.id
      WHERE a.id = $id
    """.query[AppointmentDTO].option.transact(t)

  override def readByPatient(patientId: UUID, offset: Int, size: Int): IO[List[AppointmentDTO]] =
    sql"""
      SELECT a.id, a.patient_id, a.doctor_id, a.office_id, a.service_id, a.appointment_time, a.end_time,
             a.status::text, a.notes, a.confirmation_token, a.confirmed_at, a.reminder_sent, a.created_at, a.updated_at,
             CONCAT(d.first_name, ' ', d.last_name) as doctor_name,
             st.name as service_name,
             o.name as office_name,
             NULL as patient_name,
             NULL as patient_email
      FROM appointment a
      JOIN doctor d ON a.doctor_id = d.id
      JOIN service s ON a.service_id = s.id
      JOIN service_type st ON s.type_id = st.id
      JOIN office o ON a.office_id = o.id
      WHERE a.patient_id = $patientId
      ORDER BY a.appointment_time DESC
      LIMIT $size OFFSET $offset
    """.query[AppointmentDTO].to[List].transact(t)

  override def readByDoctor(doctorId: UUID, offset: Int, size: Int): IO[List[AppointmentDTO]] =
    sql"""
      SELECT a.id, a.patient_id, a.doctor_id, a.office_id, a.service_id, a.appointment_time, a.end_time,
             a.status::text, a.notes, a.confirmation_token, a.confirmed_at, a.reminder_sent, a.created_at, a.updated_at,
             NULL as doctor_name,
             st.name as service_name,
             o.name as office_name,
             CONCAT(u.first_name, ' ', u.last_name) as patient_name,
             u.email as patient_email
      FROM appointment a
      JOIN service s ON a.service_id = s.id
      JOIN service_type st ON s.type_id = st.id
      JOIN office o ON a.office_id = o.id
      JOIN users u ON a.patient_id = u.id
      WHERE a.doctor_id = $doctorId
      ORDER BY a.appointment_time DESC
      LIMIT $size OFFSET $offset
    """.query[AppointmentDTO].to[List].transact(t)

  override def readByDoctorAndDate(doctorId: UUID, date: Instant): IO[List[AppointmentDTO]] = {
    val startOfDay = date.toString.take(10) + "T00:00:00Z"
    val endOfDay = date.toString.take(10) + "T23:59:59Z"
    sql"""
      SELECT a.id, a.patient_id, a.doctor_id, a.office_id, a.service_id, a.appointment_time, a.end_time,
             a.status::text, a.notes, a.confirmation_token, a.confirmed_at, a.reminder_sent, a.created_at, a.updated_at,
             NULL as doctor_name,
             st.name as service_name,
             o.name as office_name,
             CONCAT(u.first_name, ' ', u.last_name) as patient_name,
             u.email as patient_email
      FROM appointment a
      JOIN service s ON a.service_id = s.id
      JOIN service_type st ON s.type_id = st.id
      JOIN office o ON a.office_id = o.id
      JOIN users u ON a.patient_id = u.id
      WHERE a.doctor_id = $doctorId
        AND a.appointment_time >= $startOfDay::timestamptz
        AND a.appointment_time <= $endOfDay::timestamptz
        AND a.status NOT IN ('CANCELLED', 'REJECTED')
      ORDER BY a.appointment_time
    """.query[AppointmentDTO].to[List].transact(t)
  }

  override def readByOffice(officeId: UUID, offset: Int, size: Int): IO[List[AppointmentDTO]] =
    sql"""
      SELECT a.id, a.patient_id, a.doctor_id, a.office_id, a.service_id, a.appointment_time, a.end_time,
             a.status::text, a.notes, a.confirmation_token, a.confirmed_at, a.reminder_sent, a.created_at, a.updated_at,
             CONCAT(d.first_name, ' ', d.last_name) as doctor_name,
             st.name as service_name,
             o.name as office_name,
             CONCAT(u.first_name, ' ', u.last_name) as patient_name,
             u.email as patient_email
      FROM appointment a
      JOIN doctor d ON a.doctor_id = d.id
      JOIN service s ON a.service_id = s.id
      JOIN service_type st ON s.type_id = st.id
      JOIN office o ON a.office_id = o.id
      JOIN users u ON a.patient_id = u.id
      WHERE a.office_id = $officeId
      ORDER BY a.appointment_time DESC
      LIMIT $size OFFSET $offset
    """.query[AppointmentDTO].to[List].transact(t)

  override def readByConfirmationToken(token: String): IO[Option[AppointmentDTO]] =
    sql"""
      SELECT a.id, a.patient_id, a.doctor_id, a.office_id, a.service_id, a.appointment_time, a.end_time,
             a.status::text, a.notes, a.confirmation_token, a.confirmed_at, a.reminder_sent, a.created_at, a.updated_at,
             CONCAT(d.first_name, ' ', d.last_name) as doctor_name,
             st.name as service_name,
             o.name as office_name,
             u.first_name as patient_name,
             u.email as patient_email
      FROM appointment a
      JOIN doctor d ON a.doctor_id = d.id
      JOIN service s ON a.service_id = s.id
      JOIN service_type st ON s.type_id = st.id
      JOIN office o ON a.office_id = o.id
      JOIN users u ON a.patient_id = u.id
      WHERE a.confirmation_token = $token
    """.query[AppointmentDTO].option.transact(t)

  override def readPendingReminders(beforeTime: Instant): IO[List[AppointmentDTO]] =
    sql"""
      SELECT a.id, a.patient_id, a.doctor_id, a.office_id, a.service_id, a.appointment_time, a.end_time,
             a.status::text, a.notes, a.confirmation_token, a.confirmed_at, a.reminder_sent, a.created_at, a.updated_at,
             CONCAT(d.first_name, ' ', d.last_name) as doctor_name,
             st.name as service_name,
             o.name as office_name,
             u.first_name as patient_name,
             u.email as patient_email
      FROM appointment a
      JOIN doctor d ON a.doctor_id = d.id
      JOIN service s ON a.service_id = s.id
      JOIN service_type st ON s.type_id = st.id
      JOIN office o ON a.office_id = o.id
      JOIN users u ON a.patient_id = u.id
      WHERE a.status = 'CONFIRMED'
        AND a.reminder_sent = false
        AND a.appointment_time <= $beforeTime
        AND a.appointment_time > NOW()
    """.query[AppointmentDTO].to[List].transact(t)

  override def update(id: UUID, appointment: AppointmentDTO): IO[Int] =
    sql"""
      UPDATE appointment
      SET appointment_time = ${appointment.appointmentTime},
          end_time = ${appointment.endTime},
          status = ${appointment.status}::appointment_status,
          notes = ${appointment.notes},
          updated_at = NOW()
      WHERE id = $id
    """.update.run.transact(t)

  override def updateStatus(id: UUID, status: String): IO[Int] =
    sql"""
      UPDATE appointment
      SET status = $status::appointment_status, updated_at = NOW()
      WHERE id = $id
    """.update.run.transact(t)

  override def confirmAppointment(id: UUID): IO[Int] =
    sql"""
      UPDATE appointment
      SET status = 'CONFIRMED'::appointment_status, confirmed_at = NOW(), updated_at = NOW()
      WHERE id = $id
    """.update.run.transact(t)

  override def markReminderSent(id: UUID): IO[Int] =
    sql"""
      UPDATE appointment
      SET reminder_sent = true, updated_at = NOW()
      WHERE id = $id
    """.update.run.transact(t)

  override def delete(id: UUID): IO[Int] =
    sql"DELETE FROM appointment WHERE id = $id".update.run.transact(t)

  override def hasConflict(doctorId: UUID, startTime: Instant, endTime: Instant, excludeId: Option[UUID] = None): IO[Boolean] = {
    val excludeClause = excludeId.map(id => fr"AND id != $id").getOrElse(fr"")
    (fr"""
      SELECT EXISTS(
        SELECT 1 FROM appointment
        WHERE doctor_id = $doctorId
          AND status NOT IN ('CANCELLED', 'REJECTED')
          AND (
            (appointment_time <= $startTime AND end_time > $startTime)
            OR (appointment_time < $endTime AND end_time >= $endTime)
            OR (appointment_time >= $startTime AND end_time <= $endTime)
          )
    """ ++ excludeClause ++ fr")").query[Boolean].unique.transact(t)
  }
}
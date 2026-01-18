package db

import cats.effect.IO
import config.ConfigUtils
import config.objects.DbConnectionConfig
import org.flywaydb.core.Flyway

object FlywayMigratorApp {

  def migrate(): IO[Unit] =
    for {
      _   <- IO.println("Migrating database")
      cfg <- ConfigUtils.loadAndParse[DbConnectionConfig](
        "application.conf",
        "reference.db"
      )
      _ <- IO.println(s"Config loaded: $cfg")

      locations = cfg.locations.getOrElse(Nil).toArray // Array[String]

      flyway = Flyway
        .configure()
        .dataSource(cfg.url, cfg.user, cfg.password)
        .group(true)
        .outOfOrder(false)
        .locations(locations *) // Scala 2.13 varargs expansion
        .load()

      result <- IO(flyway.migrate())
      _      <- IO.println(s"Migration executed: ${result.migrationsExecuted}")
    } yield ()

}

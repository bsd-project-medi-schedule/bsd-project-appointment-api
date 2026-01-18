package db

import cats.effect.*
import config.objects.DbConnectionConfig
import doobie.*
import doobie.hikari.HikariTransactor
import scala.concurrent.ExecutionContext

final case class DbContext(
  executionContext: ExecutionContext,
  transactor: Transactor[IO]
)

object DbContext {
  def apply(config: DbConnectionConfig): Resource[IO, DbContext] =
    for {
      executionContext <- ExecutionContexts.fixedThreadPool[IO](config.fixedPoolSize.getOrElse(8))
      transactor <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        config.url,
        config.user,
        config.password,
        executionContext
      )
    } yield DbContext(
      executionContext,
      transactor
    )

}

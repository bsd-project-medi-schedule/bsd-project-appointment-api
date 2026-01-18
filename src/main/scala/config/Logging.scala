package config

import cats.effect.IO
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

trait Logging {

  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

}

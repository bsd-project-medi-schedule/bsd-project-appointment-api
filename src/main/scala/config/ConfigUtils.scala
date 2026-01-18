package config

import cats.effect.IO
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import pureconfig.ConfigSource
import pureconfig.ConfigReader

object ConfigUtils {

  def loadAndParse[A: ConfigReader](path: String, configName: String): IO[A] = {
    val rawConfigIO = IO.delay(
      ConfigFactory
        .load(path)
        .withFallback(
          ConfigFactory.load(getClass.getClassLoader)
        )
        .getConfig(configName)
        .resolve()
    )

    for {
      rawConfig    <- rawConfigIO
      parsedConfig <- tryParse(rawConfig)
    } yield parsedConfig
  }

  private def tryParse[A: ConfigReader](config: Config): IO[A] =
    ConfigSource.fromConfig(config).load[A] match {
      case Left(failures) =>
        IO.raiseError(
          new RuntimeException(
            s"Cannot convert configuration to config class. Errors: $failures. " +
              s"ClassName: ${implicitly[ConfigReader[A]].getClass.getName}"
          )
        )
      case Right(value) => IO(value)
    }
}

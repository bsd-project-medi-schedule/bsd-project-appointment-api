package config.objects

import config.ConfigCompanionBase
import pureconfig.generic.semiauto.deriveConvert
import pureconfig.ConfigConvert

final case class DbConnectionConfig(
  url: String,
  user: String,
  password: String,
  fixedPoolSize: Option[Int],
  locations: Option[List[String]],
)

object DbConnectionConfig extends ConfigCompanionBase[DbConnectionConfig] {
  implicit override val configConvert: ConfigConvert[DbConnectionConfig] =
    deriveConvert[DbConnectionConfig]
}

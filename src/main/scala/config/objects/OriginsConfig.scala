package config.objects

import config.ConfigCompanionBase
import pureconfig.generic.semiauto.deriveConvert
import pureconfig.ConfigConvert

final case class OriginsConfig(
  ssl: Boolean,
  tHost: String,
  tPort: Option[Int]
)

object OriginsConfig extends ConfigCompanionBase[OriginsConfig] {
  implicit override val configConvert: ConfigConvert[OriginsConfig] = deriveConvert[OriginsConfig]
}

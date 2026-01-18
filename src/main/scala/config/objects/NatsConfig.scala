package config.objects

import config.ConfigCompanionBase
import pureconfig.ConfigConvert
import pureconfig.generic.semiauto.deriveConvert

final case class NatsConfig(
  natsHost: String,
  natsPort: Int,
  connectionName: String,
  streamName: String,
  durablePrefix: String,
  streamSubjects: List[String]
)

object NatsConfig extends ConfigCompanionBase[NatsConfig] {
  implicit override val configConvert: ConfigConvert[NatsConfig] = deriveConvert[NatsConfig]
}

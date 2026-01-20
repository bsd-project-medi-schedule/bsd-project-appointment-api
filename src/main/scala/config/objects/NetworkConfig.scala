package config.objects

import config.ConfigCompanionBase
import pureconfig.generic.semiauto.deriveConvert
import pureconfig.ConfigConvert

final case class NetworkConfig(
  appHost: String,
  appPort: Int,
  trustedOrigins: List[OriginsConfig],
  secureCookies: Boolean = false,
  authEndpoint: String,
  cookieDomain: Option[String] = None
)

object NetworkConfig extends ConfigCompanionBase[NetworkConfig] {
  implicit override val configConvert: ConfigConvert[NetworkConfig] = deriveConvert[NetworkConfig]
}

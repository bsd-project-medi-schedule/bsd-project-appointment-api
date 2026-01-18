package config.objects

import config.ConfigCompanionBase
import pureconfig.ConfigConvert
import pureconfig.generic.semiauto.deriveConvert

final case class AuthConfig(
  issuer: String,
  secretKey: String
)

object AuthConfig extends ConfigCompanionBase[AuthConfig] {
  implicit override val configConvert: ConfigConvert[AuthConfig] = deriveConvert[AuthConfig]
}

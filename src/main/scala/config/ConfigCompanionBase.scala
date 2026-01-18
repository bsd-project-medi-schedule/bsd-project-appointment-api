package config

import pureconfig.ConfigConvert

import scala.reflect.ClassTag

abstract class ConfigCompanionBase[T: ClassTag] {
  def configConvert: ConfigConvert[T]
}

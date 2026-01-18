import sbt.compilerPlugin
import sbt.librarymanagement.syntax.*

object Dependencies {
  lazy val projectScalaVersion = "2.13.18"

  val http4sVersion          = "0.23.30"
  lazy val http4sDsl         = "org.http4s" %% "http4s-dsl"          % http4sVersion
  lazy val http4sServer      = "org.http4s" %% "http4s-server"       % http4sVersion
  lazy val http4sEmberServer = "org.http4s" %% "http4s-ember-server" % http4sVersion
  lazy val http4sCirce       = "org.http4s" %% "http4s-circe"        % http4sVersion

  val blazeVersion           = "0.23.17"
  lazy val http4sBlazeServer = "org.http4s" %% "http4s-blaze-server" % blazeVersion
  lazy val http4sBlazeClient = "org.http4s" %% "http4s-blaze-client" % blazeVersion

  val catsEffectVersion      = "3.5.7"
  lazy val catsEffect        = "org.typelevel" %% "cats-effect"         % catsEffectVersion
  lazy val catsEffectLaws    = "org.typelevel" %% "cats-effect-laws"    % catsEffectVersion
  lazy val catsEffectTestKit = "org.typelevel" %% "cats-effect-testkit" % catsEffectVersion

  val slf4jVersion       = "2.0.17"
  lazy val slf4jForJcl   = "org.slf4j" % "jcl-over-slf4j"   % slf4jVersion
  lazy val slf4jForLog4j = "org.slf4j" % "log4j-over-slf4j" % slf4jVersion
  lazy val slf4j         = "org.slf4j" % "slf4j-api"        % slf4jVersion

  lazy val logBack = "ch.qos.logback" % "logback-classic" % "1.5.17"

  val doobieVersion       = "1.0.0-RC8"
  lazy val doobieCore     = "org.tpolecat" %% "doobie-core"     % doobieVersion
  lazy val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % doobieVersion
  lazy val doobieH2       = "org.tpolecat" %% "doobie-h2"       % doobieVersion
  lazy val doobieHikari   = "org.tpolecat" %% "doobie-hikari"   % doobieVersion

  val fs2Version = "3.12.2"
  lazy val fs2Core = "co.fs2"        %% "fs2-core"      % fs2Version
  lazy val fs2IO = "co.fs2"        %% "fs2-io"        % fs2Version

  lazy val nats = "io.nats"        % "jnats"         % "2.25.1"

  val circeVersion      = "0.14.10"
  lazy val circeCore    = "io.circe" %% "circe-core"    % circeVersion
  lazy val circeGeneric = "io.circe" %% "circe-generic" % circeVersion
  lazy val circeParser  = "io.circe" %% "circe-parser"  % circeVersion

  lazy val nimbusJwt   = "com.nimbusds" % "nimbus-jose-jwt" % "10.0.2"
  lazy val nimbusOauth = "com.nimbusds" % "oauth2-oidc-sdk" % "11.23.1"
  lazy val jbcrypt     = "org.mindrot"  % "jbcrypt"         % "0.4"

  lazy val bouncyCastleBcpkix = "org.bouncycastle" % "bcpkix-jdk15on" % "1.70"
  lazy val bouncyCastleBcprov = "org.bouncycastle" % "bcprov-jdk15on" % "1.70"

  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % "0.17.8"

  lazy val scalafixExternalRules = "org.typelevel" %% "typelevel-scalafix" % "0.5.0"

  lazy val compilerPlugins = compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

  lazy val postgresDriver = "org.postgresql" % "postgresql"  % "42.7.5"

  val flywayVersion = "11.20.1"
  lazy val flywayCore     = "org.flywaydb" % "flyway-core"                % flywayVersion
  lazy val flywayPostgres = "org.flywaydb" % "flyway-database-postgresql" % flywayVersion

  lazy val twilio = "com.twilio.sdk" % "twilio" % "10.7.2"

  lazy val apachePoi = "org.apache.poi" % "poi-ooxml" % "5.4.1"

}

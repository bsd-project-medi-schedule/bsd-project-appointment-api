import Dependencies.*

ThisBuild / version := "0.1.0-SNAPSHOT"

val serverLogo =
  s"""
     |    ____  ________  __     _____
     |   / __ )/ ____/ / / /    / ___/___  ______   _____  _____
     |  / __  / /   / /_/ /_____\\__ \\/ _ \\/ ___/ | / / _ \\/ ___/
     | / /_/ / /___/ __  /_____/__/ /  __/ /   | |/ /  __/ /
     |/_____/\\____/_/ /_/     /____/\\___/_/    |___/\\___/_/
     |
     |
     |""".stripMargin
logo := serverLogo

lazy val root = Project(id = "appointment-api", base = file(".")).in(file("."))
  .settings(
    name := "appointment-api",
  )

libraryDependencies ++= Seq(
  http4sDsl,
  http4sEmberServer,
  http4sBlazeServer,
  http4sBlazeClient,
  http4sCirce,
  catsEffect,
  catsEffectLaws,
  catsEffectTestKit,
  slf4jForJcl,
  slf4jForLog4j,
  slf4j,
  logBack,
  doobieCore,
  doobiePostgres,
  doobieHikari,
  doobieH2,
  circeCore,
  circeGeneric,
  circeParser,
  nimbusJwt,
  nimbusOauth,
  jbcrypt,
  pureConfig,
  scalafixExternalRules,
  postgresDriver,
  flywayCore,
  flywayPostgres,
  bouncyCastleBcpkix,
  bouncyCastleBcprov,
  fs2Core,
  fs2IO,
  nats,
  apachePoi
)

inThisBuild(
  List(
    organization := "net.bchportal",
    scalaVersion := projectScalaVersion,
    scalacOptions ++= Seq(
      "-Wconf:any:warning-verbose",
      "-Xsource:3-cross",
      "-target:jvm-21",
      "-release",
      "21"
    ),
    javacOptions ++= Seq(
      "--release",
      "21",
    ),
    scalacOptions ++= Seq(
      "-Wconf:src=src_managed/.*:silent",
      "-Wconf:src=twirl/.*:silent"
    ),
    libraryDependencies += compilerPlugins,
  )
)

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case x if x.endsWith("reference.conf") => MergeStrategy.concat
  case x => MergeStrategy.first
}

assembly / mainClass := Some("Main")
assembly / assemblyJarName := "appointment-api.jar"

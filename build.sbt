name := "scala-torrent"

version := "0.1-SNAPSHOT"

libraryDependencies ++= {
  val akkaV = "2.5.4"
  val scalaMockV = "3.6.0"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-slf4j" % akkaV,
    "io.spray" %% "spray-client" % "1.3.4",
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6",
    "com.typesafe" % "config" % "1.3.1",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1",
    "org.scala-lang.modules" %% "scala-pickling" % "0.10.1",
    "org.scala-sbt" %% "sbinary" % "0.4.4",
    "org.typelevel" %% "cats-core" % "0.9.0",
    "com.chuusai" %% "shapeless" % "2.3.2",
    "org.jline" % "jline" % "3.5.2",
    "com.typesafe.akka" %% "akka-testkit" % akkaV % "it,test",
    "org.scalatest" %% "scalatest" % "3.0.4" % "it,test",
    "org.scalamock" %% "scalamock-core" % scalaMockV % "it,test",
    "org.scalamock" %% "scalamock-scalatest-support" % scalaMockV % "it,test",
    "org.mockito" % "mockito-all" % "1.10.19" % "it,test"
  )
}

lazy val commonSettings = Seq(
  scalaVersion := "2.11.4" //TODO 2.12
)
lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    commonSettings,
    Defaults.itSettings
  )

//testOptions in Test += Tests.Argument("-oS") //stack trace verbosity (in tests)
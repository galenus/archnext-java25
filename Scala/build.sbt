name := "j25triviabot-scala"

version := "0.1"

scalaVersion := "2.13.3"

lazy val akkaVersion = "2.6.6"

resolvers ++= Seq(
  "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"
)

libraryDependencies ++= Seq(
  "org.telegram" % "telegrambots" % "4.9",
  "org.telegram" % "telegrambotsextensions" % "4.9",
  "org.jsoup" % "jsoup" % "1.13.1",
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "org.glassfish.jersey.core" % "jersey-client" % "2.31",
  "org.glassfish.jersey.media" % "jersey-media-json-jackson" % "2.31"
)

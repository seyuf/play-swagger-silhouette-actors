name := """web-rtc"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

resolvers += Resolver.jcenterRepo
libraryDependencies ++= Seq(
  cache,
  filters,
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,
  "com.mohiva" %% "play-silhouette" % "4.0.0",
  "com.mohiva" %% "play-silhouette-password-bcrypt" % "4.0.0",
  "com.mohiva" %% "play-silhouette-testkit" % "4.0.0" % "test",
  "com.mohiva" %% "play-silhouette-crypto-jca" % "4.0.0",
  "com.mohiva" %% "play-silhouette-cas" % "4.0.0",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.11.11",
  "com.mohiva" %% "play-silhouette-persistence-reactivemongo" % "4.0.0-RC1",
  "org.webjars" %% "webjars-play" % "2.5.0-2",
  //"org.reactivemongo" %% "play2-reactivemongo" % "0.12.0-SNAPSHOT",
  "com.typesafe.play" % "play-mailer_2.11" % "5.0.0-M1",
  "net.codingwell" %% "scala-guice" % "4.0.1",
  "com.iheart" %% "ficus" % "1.2.3",
  "com.iheart" %% "play-swagger" % "0.4.0",
  "org.webjars" % "swagger-ui" % "2.1.4"

)


resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers += Resolver.sonatypeRepo("snapshots")

routesGenerator := InjectedRoutesGenerator

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

//activator run settings
PlayKeys.devSettings := Seq("play.server.http.port" -> "7000")

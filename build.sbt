// THIS NEEDS TO POINT TO YOUR OMNIDRIVER LOCATION
val omnidriverLocation = "C:\\Program Files\\Ocean Optics\\OmniDriver\\OOI_HOME"

/* Project settings */

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .settings(name := "usb2000")

/* Plotting */

libraryDependencies += "org.plotly-scala" %% "plotly-almond" % "0.8.5"

/* Webserver stuff */

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-server" % "0.23.26",
  "org.http4s" %% "http4s-dsl"          % "0.23.26",
  "org.http4s" %% "http4s-circe"        % "0.23.26",
  "io.circe"   %% "circe-generic"       % "0.14.14",
  "io.circe"   %% "circe-parser"        % "0.14.14"
)

/* Logging */

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-api" % "1.7.36",
  "ch.qos.logback" % "logback-classic" % "1.2.13"
)

/* Omnidriver */

Compile / unmanagedJars ++= {
  val jarDir = file(omnidriverLocation)
  (jarDir ** "*.jar").classpath
}

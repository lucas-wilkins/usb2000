import scala.util.Using
import scala.sys.exit
import scala.concurrent.duration._

import java.nio.file.{Files, Paths}
import java.io.PrintWriter

import org.slf4j.LoggerFactory

import cats.effect._
import cats.implicits.toSemigroupKOps

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.server._
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.http4s.implicits._
import com.comcast.ip4s.{Host, Port}
import io.circe.{Decoder, Json, ParsingFailure}
import io.circe.parser.decode

import com.oceanoptics.omnidriver.api.wrapper.Wrapper



object SpectrometerServer extends IOApp {

  private val logger = LoggerFactory.getLogger(this.getClass)

  /*
   * Init code, open spectrometers
   */
  val omnidriver = new Wrapper()

  omnidriver.openAllSpectrometers()

  omnidriver.getNumberOfSpectrometersFound() match {
    case 0 =>
      logger.error("No spectrometers connected")
      exit()
    case 1 =>
      logger.info("Spectrometer is connected")
    case _ =>
      logger.info("Multiple spectrometers connected, just using the first one")

  }

  val measurement = try {

    new Measurement(omnidriver.getWavelengths(0))

  } catch {
    case e: Exception =>
      logger.error(s"Error getting wavelengths: ${e.getMessage}")
      exit()
  }


  private var integrationTimeMicros: Int = 1000

  def setIntegrationTime(micros: Int): Unit = {
    omnidriver.setIntegrationTime(0, micros)
    integrationTimeMicros = micros
  }

  // Make sure its set
  setIntegrationTime(integrationTimeMicros)

  // Update called in main server loop
  def updateCurrentSpectrum(): Unit = {
    try {
      measurement.data = new Recording(omnidriver.getSpectrum(0), integrationTimeMicros)
    } catch {
      case e: Exception => logger.error(s"Spectrometer read failed: ${e.getMessage}")
    }
  }

  // Get the current spectrum
  def currentSpectrumData: Array[(Double, Double)] = {
    measurement.wavelengths zip measurement.relative
  }

  /*
   *  Saving
   */

  def saveFile(filename: String, overwrite: Boolean): Boolean = {

    logger.info(s"Save request '$filename' (overwrite=$overwrite)")


    val path = Paths.get(filename)

    if (!overwrite) {
      if (Files.exists(path)) {
        return false
      }
    }

    Using(new PrintWriter(path.toFile)) { writer =>
      writer.write(measurement.csvData())
    }

    true
  }

  /*
   * Server code
   */



  val spectrumHtml: String = Files.readString(Paths.get("spectrum.html"))

  val host: Host = Host.fromString("127.0.0.1").getOrElse(throw new RuntimeException("Bad host name"))
  val port: Port = Port.fromInt(8080).getOrElse(throw new RuntimeException("Bad port number"))


  // JSON decoding stuff

  case class IntegrationTime(value: Int)
  val integrationTimeDecoder: Decoder[IntegrationTime] = Decoder.forProduct1("value")(IntegrationTime.apply)

  case class SaveRequest(filename: String, overwrite: Boolean)
  val saveRequestDecoder: Decoder[SaveRequest] = Decoder.forProduct2("filename", "overwrite")(SaveRequest.apply)

  // Respond to requests for /data with the latest data as json
  private val dataRoute = HttpRoutes.of[IO] {
    case GET -> Root / "data" =>
      val json = currentSpectrumData
                  .map { case (x, y) => s"""{"x":$x,"y":$y}""" }
                  .mkString("[", ",", "]")

      val response = Response[IO](Status.Ok)
        .withEntity(json)
        .withHeaders(
          Headers(
            `Content-Type`(MediaType.application.json, Charset.`UTF-8`)
          )
        )

      IO.pure(response)

  }

  // Handle the standard http request, return the web page
  private val indexRoute = HttpRoutes.of[IO] {
    case GET -> Root =>

      val response = Response[IO](Status.Ok)
        .withEntity(spectrumHtml)
        .withHeaders(
          Headers(
            `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
          )
        )

      IO.pure(response)

  }

  // Respond to requests to set the integration time
  private val integrationTimeRoute = HttpRoutes.of[IO] {
    case req @ POST -> Root / "integrationtime" => req.as[String].flatMap { rawJson =>
      decode[IntegrationTime](rawJson)(integrationTimeDecoder) match {
        case Right(time) =>
          IO.blocking(setIntegrationTime(time.value)) *>
            Ok(s"Requested integration time of ${time.value}")
        case Left(err) =>
          logger.error(s"Got bad JSON: ${rawJson}")
          BadRequest(s"Invalid JSON: ${err.getMessage}")
      }
    }
  }

  private val saveRoute = HttpRoutes.of[IO] {
    case req @ POST -> Root / "save" => req.as[String].flatMap { rawJson =>
      decode[SaveRequest](rawJson)(saveRequestDecoder) match {
        case Right(saveReq) =>

          val success = saveFile(saveReq.filename, saveReq.overwrite)

          if (success) {
              Ok(s"Saved to ${saveReq.filename}")
            } else {
              Ok(s"${saveReq.filename} already exists")
            }

        case Left(err) =>
          logger.error(s"Got bad JSON: ${rawJson}")
          BadRequest(s"Invalid JSON: ${err.getMessage}")
      }
    }
  }

  // Respond to requests to set the white reference
  private val lightRoute = HttpRoutes.of[IO] {
    case POST -> Root / "light" =>
      IO(measurement.setLight()) *> IO(logger.info("Light spectrum set")) *> Ok("Light point set.")
  }

  // Respond to requests to clear the white reference
  private val clearLightRoute = HttpRoutes.of[IO] {
    case POST -> Root / "clearlight" =>
      IO(measurement.clearLight()) *> IO(logger.info("Light spectrum cleared")) *> Ok("Light point cleared.")
  }



  // Respond to requests to set the dark reference
  private val darkRoute = HttpRoutes.of[IO] {
    case POST -> Root / "dark" =>
      IO(measurement.setDark()) *> IO(logger.info("Dark spectrum set")) *> Ok("Dark point set.")
  }

  // All the routes as an app
  private val routes = (dataRoute
                          <+> indexRoute
                          <+> integrationTimeRoute
                          <+> saveRoute
                          <+> lightRoute
                          <+> clearLightRoute
                          <+> darkRoute).orNotFound

  def run(args: List[String]): IO[ExitCode] = {

    // Main server loop
    val server = EmberServerBuilder.default[IO]
      .withHost(host)
      .withPort(port)
      .withHttpApp(routes)
      .build
      .useForever

    // Get new data every 500 ms
    val tick = IO.sleep(100.millis) >> IO.blocking(updateCurrentSpectrum())
    lazy val tickLoop: IO[Unit] = (tick >> tickLoop).handleErrorWith(_ => IO.unit)

    // Run data generation in the background
    tickLoop.start >> server.as(ExitCode.Success)
  }

}
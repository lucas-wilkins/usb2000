
/**
 * Class representing a sample from the spectrometer
 *
 * @param intensities Measured intensities
 * @param integrationTime Integration time used for this measurement
 */

class Recording(val intensities: Array[Double], val integrationTime: Double) {
  val rate: Array[Double] = intensities.map(_ / integrationTime)

}

object Recording {
  def zero(length: Int) = new Recording(Array.fill(length)(0.0), 1)
  def one(length: Int) = new Recording(Array.fill(length)(1.0), 1)
}
import detuplise._


/**
 * Representation of a potentially normalised measurement
 *
 * @param wavelengths Wavelengths used for all the recordings
 */

class Measurement(val wavelengths: Array[Double]) {
  private var rawRecording: Recording = Recording.zero(wavelengths.length)
  private var darkRecording: Recording = Recording.zero(wavelengths.length)
  private var lightRecording: Option[Recording] = None

  private val ones = Array.fill(wavelengths.length)(1.0)

  /**
   * Dark correct a measurement
   *
   * @param raw Raw recording
   * @param dark Dark recording
   * @return Corrected recording as double array
   */
  private def darkCorrect(raw: Recording, dark: Recording): Array[Double] = {

    (raw.rate zip dark.rate).map({case (a,b) => a - b})
  }

  /**
   * Light correct already dark corrected measurements
   *
   * @param darkCorrectedRaw raw - dark
   * @param darkCorrectedLight light - dark
   * @return Light corrected measurement
   */
  private def normalise(darkCorrectedRaw: Array[Double], darkCorrectedLight: Array[Double]): Array[Double] = {
    (darkCorrectedRaw zip darkCorrectedLight).map({case (a,b) => a / b})
  }

  /**
   * @return Light spectrum corrected for dark measurement
   */
  private def darkCorrectedLight: Array[Double] = {
    lightRecording match {
      case Some(light) => darkCorrect(light, darkRecording)
      case None => ones
    }

  }

  /**
   *
   * raw - dark
   *
   * @return Dark corrected rate spectrum
   */
  def darkCorrected: Array[Double] = {
    darkCorrect(rawRecording, darkRecording)
  }

  /**
   *
   * (raw - dark) / (raw - light)
   *
   * @return Light and dark corrected rate spectrum
   */

  def relative: Array[Double] = {
    normalise(darkCorrected, darkCorrectedLight)
  }

  def data: Recording = rawRecording

  /**
   * Setter for the current raw data
   * @param data data
   */
  def data_=(data: Recording): Unit = {
    if (data.intensities.length == wavelengths.length) {
      rawRecording = data
    } else {
      throw new DataIntegrityException("Wavelength and measurement array sizes do not match")
    }
  }

  /**
   * Set the dark spectrum to the current recording
   */
  def setDark(): Unit = {
    darkRecording = rawRecording
  }


  /**
   * Set the light spectrum to the current recording
   */
  def setLight(): Unit = {
    lightRecording = Some(rawRecording)
  }

  /**
   * Clear the light spectrum
   */
  def clearLight(): Unit = {
    lightRecording = None
  }

  def csvData(): String = {

    val allData = Array(
      wavelengths,
      relative,
      rawRecording.intensities,
      rawRecording.rate,
      darkRecording.intensities,
      darkRecording.rate,
      lightRecording.map(_.intensities).getOrElse(ones),
      lightRecording.map(_.rate).getOrElse(ones))

    "Wavelength (nm), Relative, Raw, Raw Rate (/us), Dark, Dark Rate (/us), Light, Light Rate(/us)\n" +
    (0 until wavelengths.length)
      .map(i =>
        allData
          .map(_(i))
          .map(_.toString)
          .mkString(", "))
      .mkString("\n")
  }

}

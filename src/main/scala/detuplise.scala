object detuplise {
  // Main reason to use scala 3 ;)
  implicit def detuplePairs[A, B, C](f: (A, B) => C): ((A,B)) => C = (x: (A, B)) => f(x._1, x._2)

}

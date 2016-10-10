package txodds
package std

import scodec._
import scodec.bits._

import akka.util.ByteString

import cats.data.Xor
import cats.implicits._

/** Extensions for scodec datatypes */
trait ScodecInstances {
  implicit def toBitVectorOps(b: ByteVector): ByteVectorOps = new ByteVectorOps(b)
  implicit def tAttemptOps[A](a: Attempt[A]): AttemptOps[A] = new AttemptOps(a)
}

final class ByteVectorOps(val b: ByteVector) extends AnyVal {
  def toByteString: ByteString = ByteString(b.toByteBuffer)
}

final class AttemptOps[A](val a: Attempt[A]) extends AnyVal {
  def toXor: Xor[Err, A] = a.toEither.toXor
}

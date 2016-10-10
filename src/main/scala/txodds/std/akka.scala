package txodds
package std

import akka.util.ByteString
import scodec.bits._

trait AkkaInstances {
  implicit def toByteStringOps(b: ByteString): ByteStringOps = new ByteStringOps(b)
}

final class ByteStringOps(val b: ByteString) extends AnyVal {
  def toByteVector: ByteVector = ByteVector(b.toByteBuffer)
}

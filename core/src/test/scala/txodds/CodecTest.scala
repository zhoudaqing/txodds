package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class CodecTest extends TestKit(ActorSystem("CodecTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "A Codec actor" must { 
    "decode messages using its codec and forward them on to listeners" in(pending)
    "encode messages using its codec and forward them on to listeners" in(pending)
  }
}

/** Message is sent from the Server to the Writer */
class OffsetSizeCodecTest extends FunSpec with GeneratorDrivenPropertyChecks {
  it("should encode and decode an offset and a size")(pending)
}

/** Message is sent from the Writer to the Server */
class WriteNumberCodecTest extends FunSpec with GeneratorDrivenPropertyChecks {
  it("should encode and decode a single int")(pending)
}

/** Message is sent from the Reader to the Server */
class NextNumberRequestCodecTest extends FunSpec with GeneratorDrivenPropertyChecks {
  it("should encode and decode a request for the next number for a UUID")(pending)
}

/** Message is sent from the Server to the Reader */
class NextNumberResponseCodecTest extends FunSpec with GeneratorDrivenPropertyChecks {
  it("should encode and decode a response containing a UUID and the next number")(pending)
}

/** Message is sent from the Server to the Reader */
class EndOfSequenceCodecTest extends FunSpec with GeneratorDrivenPropertyChecks {
  it("should encode and decode a response containing a UUID and a -1")(pending)
}

/** Message is sent from the Reader to the Server */
class StartSequenceCodecTest extends FunSpec with GeneratorDrivenPropertyChecks {
  it("should encode and decode a request containing a UUID and a 0")(pending)
}

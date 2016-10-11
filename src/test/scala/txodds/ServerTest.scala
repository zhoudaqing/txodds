package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.io._
import akka.actor._
import akka.testkit._

import cats.data._

import scodec.codecs._

import scala.concurrent.duration._

import java.net.InetSocketAddress
import java.util.UUID

import Tcp._
import Headers._
import Codecs._

class ServerTest extends TestKit(ActorSystem("ServerTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with GivenWhenThen with Inside {
 
  override def afterAll {
    expectNoMsg()
    TestKit.shutdownActorSystem(system)
  }
 
  val port = new InetSocketAddress(0)
  val reporter = TestProbe("reporter")
  val database = TestProbe("database")

  "A Server system" must {
    "bind itself to a TCP port" in {
      val tcpActor = TestProbe("tcp")

      When("the server starts")
      val serverSystem = system.actorOf(ServerSystem.props(tcpActor.ref, port, 
        10 seconds, 30 minutes, 1, reporter.ref, database.ref))
      Then("it should bind to the tcp port")
      tcpActor.expectMsgType[Bind]

      When("the writer connects")
      val writer = TestProbe("writer")
      val server = tcpActor.lastSender
      writer.send(server, Connected(port, port))
      Then("a handler should be registered")
      val writerHandler = writer.expectMsgType[Register].handler
      And("A keepalive should be sent")
      writer.expectMsgType[Write]
      writer.send(writerHandler, Received((encode(headerCodec)(writeGreet)).toByteVector.toByteString))

      When("the reader connects")
      val reader = TestProbe("reader")
      reader.send(server, Connected(port, port))
      Then("a handler should be registered")
      val readerHandler = reader.expectMsgType[Register].handler
      And("A keepalive should be sent")
      reader.expectMsgType[Write]

      When("the reader requests a sequence")
      val data = encode(headerCodec ~ startSequenceCodec)((startSequence, UUID.randomUUID()))
      reader.send(readerHandler, Received(data.toByteVector.toByteString))

      Then("the writer should be asked for a sequence")
      val writerRequest = writer.expectMsgType[Write]
      val xor = (headerCodec ~ sequenceRequestCodec).decode(writerRequest.data.toBitVector).toXor
      inside(xor) {
        case Xor.Right(result) => val (header, request) = result.value
          header should ===(writeSequenceRequest)
          val seq = SequenceGenerator(request.offset, request.size)
          When("the writer responds")
          seq.foreach { i =>
            writer.send(writerHandler, Received(encode(headerCodec ~ int32)((writeSequenceResponse, i))
              .toByteVector.toByteString))
          }
          Then("the reader should receive a sequence")
          reader.expectMsgType[Write]
      }
    }
  }
}

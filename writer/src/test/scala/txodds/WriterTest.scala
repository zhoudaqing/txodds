package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.io._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._
import java.net.InetSocketAddress

import cats._
import cats.data._

class WriterTest extends TestKit(ActorSystem("WriterTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with GivenWhenThen with Inside {
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
  val remote = new InetSocketAddress(0)
 
  var tcpActor: TestProbe = _
  var connectionActor: TestProbe = _
  var writerSystem: ActorRef = _

  "A Writer system" must { 
    "generate a sequence of numbers" in {
      Given("a writer system")
      When("the system starts")
      tcpActor = TestProbe("tcpActor")
      connectionActor = TestProbe("connection")
      writerSystem = system.actorOf(WriterSystem.props(1 second, 10 seconds, tcpActor.ref, remote),
        "writer-system-0")

      Then("it should connect to the remote port via tcp")
      checkTcpConnection()

      And("it should send a keepalive message")
      checkKeepAlive()

      When("the keepalive is responded to")
      respondToKeepAlive()

      And("it gets a sequence request")
      sendSequenceRequest(SequenceRequest(5, 3))
      Then("it should send a response for each number in the sequence")
      expectSequenceResponse(5)
      expectSequenceResponse(6)
      expectSequenceResponse(7)
    }

    "terminate on not receiving keepalive responses" in {
      Given("a writer system")
      When("the system starts")
      tcpActor = TestProbe("tcpActor")
      writerSystem = system.actorOf(WriterSystem.props(1 second, 10 seconds, tcpActor.ref, remote), 
        "writer-system-1")
      val watcher = TestProbe("watcher")
      watcher watch writerSystem

      Then("it should connect to the remote port via tcp")
      checkTcpConnection()

      And("it should send a keepalive message")
      checkKeepAlive()

      When("the keepalive is not responded to")
      Thread.sleep((1 second).toMillis)
      
      Then("it should terminate")
      watcher.expectTerminated(writerSystem)

    }

    def checkTcpConnection(): Unit = {
      tcpActor.expectMsg(Tcp.Connect(remote))
      val client = tcpActor.lastSender
      connectionActor.send(client, Tcp.Connected(remote, remote))
      connectionActor.expectMsg(Tcp.Register(client))
    }

    def checkKeepAlive(): Unit = {
      val writeMessage = connectionActor.expectMsgType[Tcp.Write]
      val data = writeMessage.data.toByteVector
      inside(Codecs.headerCodec.decode(data.toBitVector).toXor) {
        case Xor.Right(result) => result.value should ===(Headers.keepAliveRequest)
      }
    }

    def respondToKeepAlive(): Unit = {
      val client = tcpActor.lastSender
      val data = Codecs.headerCodec.encode(Headers.keepAliveResponse).toXor match {
        case Xor.Right(data) => data.toByteVector.toByteString
        case Xor.Left(err) => fail(s"Could not encode header $err")
      }
      client ! Tcp.Received(data)
    }

    def sendSequenceRequest(request: SequenceRequest): Unit = {
      val client = tcpActor.lastSender
      val data = (Codecs.headerCodec ~ Codecs.sequenceRequestCodec)
        .encode((Headers.writeSequenceRequest, request)).toXor match {
        case Xor.Right(data) => data.toByteVector.toByteString
        case Xor.Left(err) => fail(s"Could not encode sequence request $err")
      }

      client ! Tcp.Received(data)
    }

    def expectSequenceResponse(expected: Int): Unit = {
      val msg = connectionActor.expectMsgType[Tcp.Write]
      (Codecs.headerCodec ~ Codecs.writeNumberCodec).decode(msg.data.toByteVector.toBitVector).toXor match {
        case Xor.Left(err) => fail(s"could not decode sequence response $err")
        case Xor.Right(result) => 
          val (header, number) = result.value
          header should ===(Headers.writeSequenceResponse)
          number should ===(expected)
      }
    }
  }
}

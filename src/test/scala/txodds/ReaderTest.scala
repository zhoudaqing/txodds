package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.io._
import akka.testkit._

import scala.concurrent.duration._

import cats._
import cats.data._

import java.net.InetSocketAddress
import java.util.UUID

class ReaderTest extends TestKit(ActorSystem("ReaderTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with GivenWhenThen with Inside {
 
  override def afterAll {
    expectNoMsg()
    TestKit.shutdownActorSystem(system)
  }

  val remote = new InetSocketAddress(0)
  var tcpActor: TestProbe = _
  var connectionActor: TestProbe = _
  var readerSystem: ActorRef = _ 
  var reporter = TestProbe("reporter")

  "A Reader system" must { 
    "start by sending UUIDs" in {
      When("The reader starts")
      tcpActor = TestProbe("tcpActor")
      connectionActor = TestProbe("connection")
      readerSystem = system.actorOf(ReaderSystem.props(1 second, 10 seconds, tcpActor.ref, remote, 3, reporter.ref),
        "reader-system-0")

      Then("it should connect to the remote port via tcp")
      checkTcpConnection()
      And("it should send a keepalive message")
      checkKeepAlive()
      And("it should send 3 requests")
      checkRequest()
      checkRequest()
      checkRequest()
    }

    "receive sequential integers for each UUID" in {
      When("The reader starts")
      tcpActor = TestProbe("tcpActor")
      connectionActor = TestProbe("connection")
      readerSystem = system.actorOf(ReaderSystem.props(1 second, 10 seconds, tcpActor.ref, remote, 1, reporter.ref),
        "reader-system-1")

      Then("it should connect to the remote port via tcp")
      checkTcpConnection()
      And("it should send a keepalive message")
      checkKeepAlive()
      And("it should send a request")
      val id = checkRequest()
      
      When("it receives a keepalive response")
      respondToKeepAlive()

      And("the request is responded to")
      respondToRequest(id, 3)

      Then("it should send a next number request")
      checkNextNumberRequest()

      When("the sequence is terminated")
      sendTermination(id)
      Then("it should confirm termination")
      checkTerminationConfirmation()

      And("it should start a new sequence")
      checkRequest()
    }
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

  def checkRequest(): UUID = {
    val write = connectionActor.expectMsgType[Tcp.Write]
    val data = (Codecs.headerCodec ~ Codecs.startSequenceCodec).decode(write.data.toByteVector.toBitVector).toXor.map(_.value)
    data match {
      case Xor.Right((h, id)) => h should ===(Headers.startSequence)
        id
      case Xor.Left(err) => fail(s"Could not decode start sequence request $err")
    }
  }

  def respondToRequest(id: UUID, number: Int) = {
    val client = tcpActor.lastSender
    val data = (Codecs.headerCodec ~ Codecs.nextNumberResponseCodec).encode((Headers.nextNumber, NextNumber(id, number))).toXor match {
      case Xor.Right(d) => d.toByteVector.toByteString
      case Xor.Left(err) => fail(s"could not encode next number $err")
    }
    client ! Tcp.Received(data)
  }

  def checkNextNumberRequest(): Unit = {
    val write = connectionActor.expectMsgType[Tcp.Write]
    val xor = (Codecs.headerCodec ~ Codecs.nextNumberRequestCodec).decode(write.data.toByteVector.toBitVector).toXor
    xor match {
      case Xor.Right(result) => 
        result.value._1 should ===(Headers.nextNumberRequest)
      case _ => fail("could not decode next number request")
    }
  }

  def sendTermination(id: UUID): Unit = {
    val client = tcpActor.lastSender
    val data = (Codecs.headerCodec ~ Codecs.endOfSequenceCodec).encode((Headers.endOfSequence, id)).toXor match {
      case Xor.Right(d) => d.toByteVector.toByteString
      case Xor.Left(err) => fail(s"could not encode next number $err")
    }
    client ! Tcp.Received(data)
  }

  def checkTerminationConfirmation(): Unit = {
    val write = connectionActor.expectMsgType[Tcp.Write]
    val xor = (Codecs.headerCodec ~ Codecs.uuidCodec).decode(write.data.toByteVector.toBitVector).toXor
    xor match {
      case Xor.Right(result) => 
        result.value._1 should ===(Headers.endOfSequenceConfirm)
      case _ => fail("could not decode next number request")
    }
  }
}


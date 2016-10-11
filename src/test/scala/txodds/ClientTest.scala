package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.io._
import akka.util._
import akka.actor._
import akka.testkit._

import scodec.bits._

import java.net.InetSocketAddress

class ClientTest extends TestKit(ActorSystem("ClientTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  override def afterAll {
    expectNoMsg()
    TestKit.shutdownActorSystem(system)
  }
 
  val remote = new InetSocketAddress(0)
  var tcpActor: TestProbe = _
  var connectionActor: TestProbe = _

  override def beforeAll {
    tcpActor = TestProbe("tcp")
    connectionActor = TestProbe("connection")
  }

  "A TCP Client actor" must {
    "connect to the tcp actor on start" in {
      system.actorOf(Client.props(tcpActor.ref, remote))
      tcpActor.expectMsg(Tcp.Connect(remote))
    }

    "retry when it fails to connect" in {
      val client = system.actorOf(Client.props(tcpActor.ref, remote))
      tcpActor.expectMsg(Tcp.Connect(remote))
      tcpActor.send(client, Tcp.CommandFailed(Tcp.Connect(remote)))
      tcpActor.expectMsg(Tcp.Connect(remote))
    }

    "forward outgoing messages along the tcp connection" in {
      val client = system.actorOf(Client.props(tcpActor.ref, remote))
      tcpActor.expectMsg(Tcp.Connect(remote))
      connectionActor.send(client, Tcp.Connected(remote, remote))
      connectionActor.expectMsg(Tcp.Register(client))

      client ! Outgoing(ByteVector(0, 1, 2))
      connectionActor.expectMsgType[Tcp.Write]
    }

    "decode incoming messages using a single byte header and forward the payload to listeners" in {
      val client = system.actorOf(Client.props(tcpActor.ref, remote))
      tcpActor.expectMsg(Tcp.Connect(remote))
      connectionActor.send(client, Tcp.Connected(remote, remote))
      connectionActor.expectMsg(Tcp.Register(client))

      val listener = TestProbe("listener")
      client ! Client.RegisterListener(5, listener.ref)
      client ! Tcp.Received(ByteString(5, 3))
      listener.expectMsg(Client.Connected)
      listener.expectMsgType[Incoming]
    }

    "register multiple listeners for different byte headers" in {
      val client = system.actorOf(Client.props(tcpActor.ref, remote))
      tcpActor.expectMsg(Tcp.Connect(remote))
      connectionActor.send(client, Tcp.Connected(remote, remote))
      connectionActor.expectMsg(Tcp.Register(client))

      val firstListener = TestProbe("firstListener")
      val secondListener = TestProbe("secondListener")
      client ! Client.RegisterListener(1, firstListener.ref)
      client ! Client.RegisterListener(2, secondListener.ref)

      client ! Tcp.Received(ByteString(1, 3))
      firstListener.expectMsg(Client.Connected)
      firstListener.expectMsgType[Incoming]

      client ! Tcp.Received(ByteString(2, 3))
      secondListener.expectMsg(Client.Connected)
      secondListener.expectMsgType[Incoming]
    }
  }
}

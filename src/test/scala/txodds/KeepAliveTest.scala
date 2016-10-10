package txodds

import org.scalatest._

import akka.actor._
import akka.testkit._

import scodec.bits._

import scala.concurrent.duration._

class KeepAliveTest extends TestKit(ActorSystem("KeepAliveTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "A KeepAlive actor" must {
 
    "ping its target actor" in {
      val connection = TestProbe("connection")
      val keepAlive = system.actorOf(KeepAlive.props(10 seconds, 30 minutes, connection.ref, Nil))
      connection.expectMsgType[Client.RegisterListener]
      connection.expectMsgType[Client.RegisterListener]
      keepAlive ! Client.Connected
      connection.expectMsgType[Client.Output]
    }
    "repeat pings at the specified duration as long as pings are always responded to" in {
      val connection = TestProbe("connection")
      val keepAlive = system.actorOf(KeepAlive.props(100 milliseconds, 500 milliseconds, connection.ref, Nil))
      connection.expectMsgType[Client.RegisterListener]
      connection.expectMsgType[Client.RegisterListener]
      keepAlive ! Client.Connected
      connection.expectMsgType[Client.Output]
      connection.send(keepAlive, Client.Incoming(Headers.keepAliveResponse, ByteVector.empty))
      Thread.sleep((1 second).toMillis)
      connection.expectMsgType[Client.Output]
    }

    "send a termination message when a ping is not responded to" in {
      val connection = TestProbe("connection")
      val listener = TestProbe("listener")
      val keepAlive = system.actorOf(KeepAlive.props(2 milliseconds, 30 minutes, connection.ref, List(listener.ref)))
      connection.expectMsgType[Client.RegisterListener]
      connection.expectMsgType[Client.RegisterListener]
      keepAlive ! Client.Connected
      connection.expectMsgType[Client.Output]
      listener.expectMsg(KeepAlive.Terminate)
    }

    "send a termination message when a ping is responded to after the specified timeout" in {
      val connection = TestProbe("connection")
      val listener = TestProbe("listener")
      val keepAlive = system.actorOf(KeepAlive.props(100 milliseconds, 30 minutes, connection.ref, List(listener.ref)))
      connection.expectMsgType[Client.RegisterListener]
      connection.expectMsgType[Client.RegisterListener]
      keepAlive ! Client.Connected
      connection.expectMsgType[Client.Output]
      Thread.sleep((200 milliseconds).toMillis)
      connection.send(keepAlive, Client.Incoming(Headers.keepAliveResponse, ByteVector.empty))
      listener.expectMsg(KeepAlive.Terminate)
    }
  }
}

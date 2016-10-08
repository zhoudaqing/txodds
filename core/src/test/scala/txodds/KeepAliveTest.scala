package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class KeepAliveTest extends TestKit(ActorSystem("KeepAliveTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "A KeepAlive actor" must {
 
    "ping its target actor" in(pending)
    "send a termination message when a ping is not responded to" in(pending)
    "send a termination message when a ping is responded to after the specified timeout" in(pending)
    "repeat pings at the specified duration as long as pings are always responded to" in (pending)
  }
}

package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class ClientTest extends TestKit(ActorSystem("ClientTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "A TCP Client actor" must {
 
    "connect to the tcp actor on start" in(pending)
    "retry when it fails to connect" in(pending)
    "forward outgoing messages along the tcp connection" in(pending)
    "decode incoming messages using a single byte header and forward the payload to listeners" in (pending)
    "register a single listener per byte header" in(pending)
  }
}

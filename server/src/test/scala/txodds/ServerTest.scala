package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class ServerTest extends TestKit(ActorSystem("ServerTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "A Server system" must {
    "bind itself to a TCP port" in(pending)
    "regiter the reader and writer as clients" in(pending)
    "receive requests for 1000 sequences from the reader" in(pending)
    "request sequences from the writer" in(pending)
    "forward sequences from the writer to the reader" in(pending)
    "only keep 1000 channels open" in(pending)
    "periodically send keepalive messages" in(pending)
  }
}

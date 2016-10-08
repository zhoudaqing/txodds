package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class ReaderTest extends TestKit(ActorSystem("ReaderTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "A Reader system" must { 
    "start by sending 1000 UUIDs" in(pending)
    "receive sequential integers for each UUID" in(pending)
    "should start a new sequence when a previous sequence has finished" in(pending)
    "should report the number of completed sequences" in(pending)
    "should report the number of in-flight sequences" in(pending)
    "periodically send keepalive messages" in(pending)
  }
}


package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class WriterTest extends TestKit(ActorSystem("WriterTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "A Writer system" must { 
    "generate a sequence of numbers given a byte stream of an offset and size" in(pending)
    "periodically send keepalive messages" in(pending)
  }
}


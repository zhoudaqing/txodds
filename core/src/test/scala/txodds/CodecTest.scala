package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class CodecTest extends TestKit(ActorSystem("CodecTest")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll {
 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
 
  "A Codec actor" must { 
    "decode messages using its codec and forward them on to listeners" in(pending)
    "encode messages using its codec and forward them on to listeners" in(pending)
  }
}

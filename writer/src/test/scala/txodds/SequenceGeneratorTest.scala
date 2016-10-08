package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class SequenceGeneratorTests extends FunSpec with Matchers with GeneratorDrivenPropertyChecks {
  it("should generate sequential integers") {
    forAll(Gen.choose(0, 1000), Gen.choose(1, 100)) { (offset: Int, size: Int) =>
      val ns = SequenceGenerator(offset, size)
      ns.size should ===(size)
      ns.head should ===(offset)
    }
  }
}

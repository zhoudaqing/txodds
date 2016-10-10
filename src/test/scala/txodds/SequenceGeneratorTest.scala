package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class SequenceGeneratorTests extends FunSpec with Matchers with GeneratorDrivenPropertyChecks {
  it("should generate 5, 6, 7 for an offset of 5 and a size of 3") {
    val s = SequenceGenerator(5, 3)
    s should ===(List(5, 6, 7))
  }

  it("should generate sequential integers") {
    forAll(Gen.choose(0, 100), Gen.choose(1, 100)) { (offset, size) => 
      val s = SequenceGenerator(offset, size)
      s.head should ===(offset)
      s.size should ===(size)
    }
  }
}

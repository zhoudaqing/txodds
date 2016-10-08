package txodds

import org.scalatest._
import org.scalatest.prop._

import org.scalacheck._

import akka._
import akka.actor._
import akka.testkit._

import scala.concurrent.duration._

class SequenceGeneratorTests extends FunSpec with Matchers with GeneratorDrivenPropertyChecks {
  it("should generate sequential integers")(pending)
}

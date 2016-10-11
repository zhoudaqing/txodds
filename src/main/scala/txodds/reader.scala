package txodds

import akka.actor._
import akka.io._
import akka.http.scaladsl._
import akka.stream.ActorMaterializer

import cats.data._
import cats.implicits._

import scodec.codecs._

import Headers._
import Codecs._

import scala.concurrent.duration._
import java.net.InetSocketAddress

object ReaderApp {

  def main(args: Array[String]) = {
    implicit val system = ActorSystem("Reader")

    val http = Http()
    val materializer = ActorMaterializer()
    val reporter = system.actorOf(StatsReporter.props(http, materializer, "localhost", 8091))

    val io = IO(Tcp)
    val readerPort = new InetSocketAddress("localhost", 8090)
    val server = system.actorOf(ReaderSystem.props(5 minutes, 30 minutes, io, readerPort, 10, reporter))
  }
}


object ReaderSystem {
  def props(timeout: FiniteDuration, period: FiniteDuration, tcpActor: ActorRef, remote: InetSocketAddress, 
    poolSize: Int, reporter: ActorRef): Props = Props(new ReaderSystem(timeout, period, tcpActor, remote, poolSize, reporter))
}

class ReaderSystem(timeout: FiniteDuration, period: FiniteDuration, tcpActor: ActorRef, remote: InetSocketAddress, 
  poolSize: Int, reporter: ActorRef) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    val client = context.actorOf(Client.props(tcpActor, remote))
    val reader = context.actorOf(Reader.props(poolSize, client, reporter))
    val keepAlive = context.actorOf(KeepAlive.props(timeout, period, client, List(self)))
  }

  def receive: Receive = {
    case KeepAlive.Terminate =>
      log.error("Keepalive failed. Terminating reader")
      context stop self
  }
}

import Headers._
import Codecs._
import java.util._
import scala.collection.{mutable => mu}

object Reader {
  def props(poolSize: Int, client: ActorRef, reporter: ActorRef): Props = Props(new Reader(poolSize, client, reporter))
}

class Reader(poolSize: Int, client: ActorRef, reporter: ActorRef) extends Actor with ActorLogging {

  var completed: Int = 0
  var inFlight: Int = 0
  var previousValues: mu.Map[UUID, Option[Int]] = _

  override def preStart(): Unit = {
    client ! Client.RegisterListener(nextNumber, self)
    client ! Client.RegisterListener(endOfSequence, self)
  }

  def receive: Receive = initialize

  def initialize: Receive = {
    case Client.Connected =>
      val ids = (0 until poolSize).map(_ => UUID.randomUUID())
      previousValues = mu.Map(ids.map(id => id -> None): _*)
      ids.foreach { id =>
        val data = encode(headerCodec ~ startSequenceCodec)((startSequence, id))
        client ! Outgoing(data.toByteVector)
      }
      context become run
  }

  def run: Receive = {
    case Incoming(`nextNumber`, data) =>
      val next = decode(nextNumberCodec, data.toBitVector)
      val prev = previousValues(next.uuid)
      prev match {
        case None => inFlight = inFlight + 1
        case Some(p) =>
          if(next.number - p != 1)
            log.error(NumbersNotIncrementingError(next.uuid, p, next.number).toString())
      }
      val out = encode(headerCodec ~ uuidCodec)((nextNumberRequest, next.uuid))
      client ! Outgoing(out.toByteVector)
    case Incoming(`endOfSequence`, data) =>
      val id = decode(endOfSequenceCodec, data.toBitVector)
      val confirm = encode(headerCodec ~ uuidCodec)((endOfSequenceConfirm, id))
      client ! Outgoing(confirm.toByteVector)

      previousValues -= id
      inFlight = inFlight - 1
      completed = completed + 1

      val nextId = UUID.randomUUID()      
      previousValues += ((nextId, None))
      val start = encode(headerCodec ~ startSequenceCodec)((startSequence, nextId))
      client ! Outgoing(start.toByteVector)
      report()
  }

  def report(): Unit = {
    reporter ! s"Completed: $completed in flight: $inFlight"
  }
}
case class NumbersNotIncrementingError(id: UUID, prev: Int, next: Int) extends Throwable

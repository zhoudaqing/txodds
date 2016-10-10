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

object WriterApp {

  def main(args: Array[String]) = {
    implicit val system = ActorSystem("Writer")
    val http = Http()
    val materializer = ActorMaterializer()
    val reporter = system.actorOf(StatsReporter.props(http, materializer, "localhost", 8081))

    val io = IO(Tcp)
    val writerPort = new InetSocketAddress("localhost", 8080)
    val server = system.actorOf(WriterSystem.props(5 minutes, 30 minutes, io, writerPort, reporter))
  }
}


object WriterSystem {
  def props(timeout: FiniteDuration, period: FiniteDuration, tcpActor: ActorRef, remote: InetSocketAddress, reporter: ActorRef): Props =
    Props(new WriterSystem(timeout, period, tcpActor, remote, reporter: ActorRef))
}

class WriterSystem(timeout: FiniteDuration, period: FiniteDuration, tcpActor: ActorRef, remote: InetSocketAddress, reporter: ActorRef) 
    extends Actor with ActorLogging {
  override def preStart(): Unit = {
    val client = context.actorOf(Client.props(tcpActor, remote), "client")
    val keepAlive = context.actorOf(KeepAlive.props(timeout, period, client, List(self)), "keepalive")
    val writer = context.actorOf(Writer.props(client, reporter), "writer")
  }

  def receive: Receive = {
    case KeepAlive.Terminate => 
      log.error("Keepalive failed. Terminating system")
      context stop self
  }
}

object Writer {
  def props(client: ActorRef, reporter: ActorRef): Props = Props(new Writer(client, reporter))
}

class Writer(client: ActorRef, reporter: ActorRef) extends Actor with ActorLogging {

  var numSequences: Int = 0

  override def preStart(): Unit = {
    log.info("Starting writer")
    client ! Client.RegisterListener(writeSequenceRequest, self)
  }

  def receive: Receive = {
    case Client.Connected =>
      client ! Client.Output((encode(headerCodec)(writeGreet)).toByteVector)
    case Client.Incoming(`writeSequenceRequest`, data) =>
      val sr = decode(sequenceRequestCodec, data.toBitVector)
      log.info("received write sequence request for offset [{}] size [{}]", sr.offset, sr.size)
      SequenceGenerator(sr.offset, sr.size).foreach { i =>
        log.info("writing number [{}]", i)
        val response = encode(headerCodec ~ writeNumberCodec)((writeSequenceResponse, i))
        client ! Client.Output(response.toByteVector)
      }
      numSequences = numSequences + 1
      reporter ! s"Sequences: $numSequences"
  }
}

object SequenceGenerator {
  def apply(offset: Int, size: Int): List[Int] = 
    (0 until size).map(_ + offset).toList
}

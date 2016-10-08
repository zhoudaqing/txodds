package txodds

import akka.actor._

import cats.data._
import cats.implicits._

import scodec.codecs._

import Headers._
import Codecs._

import scala.concurrent.duration._
import java.net.InetSocketAddress

object WriterSystem {
  def props(timeout: FiniteDuration, period: FiniteDuration, tcpActor: ActorRef, remote: InetSocketAddress): Props =
    Props(new WriterSystem(timeout, period, tcpActor, remote))
}

class WriterSystem(timeout: FiniteDuration, period: FiniteDuration, tcpActor: ActorRef, remote: InetSocketAddress) 
    extends Actor with ActorLogging {
  override def preStart(): Unit = {
    val client = context.actorOf(Client.props(tcpActor, remote), "client")
    val keepAlive = context.actorOf(KeepAlive.props(timeout, period, client, List(self)), "keepalive")
    val writer = context.actorOf(Writer.props(client), "writer")
  }

  def receive: Receive = {
    case KeepAlive.Terminate => 
      log.error("Keepalive failed. Terminating system")
      context stop self
  }
}

object Writer {
  def props(client: ActorRef): Props = Props(new Writer(client))
}

class Writer(client: ActorRef) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    client ! Client.RegisterListener(writeSequenceRequest, self)
  }

  def receive: Receive = {
    case Client.Incoming(`writeSequenceRequest`, data) =>
      val xor = sequenceRequestCodec.decode(data.toBitVector).toXor
        .leftMap(DecodeError).map(_.value).flatMap { sr =>
        SequenceGenerator(sr.offset, sr.size).traverse(i =>
          (headerCodec ~ writeNumberCodec).encode((writeSequenceResponse, i)).toXor.leftMap(EncodeError))
      }
      xor match {
        case Xor.Right(data) => 
          data.foreach(d => client ! Client.Output(d.toByteVector))
        case Xor.Left(err) => log.error(err.toString)
      }
  }
}

object SequenceGenerator {
  def apply(offset: Int, size: Int): List[Int] = 
    (0 until size).map(_ + offset).toList
}

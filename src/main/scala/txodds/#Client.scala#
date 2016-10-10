package txodds

import akka.actor._
import akka.io._
import java.net.InetSocketAddress

import scodec._
import scodec.bits._

import cats.data._
import cats.implicits._

import scala.collection.{mutable => mu}

import Tcp._

object Client {
  sealed trait Received extends Any
  case class RegisterListener(header: Byte, listener: ActorRef) extends Received
  case class Output(data: ByteVector) extends AnyVal with Received

  case class Incoming(header: Byte, data: ByteVector)
  
  def props(tcpActor: ActorRef, remote: InetSocketAddress): Props =
    Props(new Client(tcpActor, remote))
}

class Client(tcpActor: ActorRef, remote: InetSocketAddress) extends Actor with ActorLogging {

  val listeners = mu.Map[Byte, ActorRef]()

  override def preStart(): Unit = {
    connect()
  }

  def connect(): Unit = tcpActor ! Connect(remote)

  def register: Receive = {
    case Client.RegisterListener(header, listener) =>
      listeners += (header -> listener)
  }

  def unconnected: Receive = register orElse ({
    case c @ Connected(_, _) =>
      val connection = sender()
      connection ! Register(self)
      context become connected(connection)
    case CommandFailed(_: Connect) =>
      log.warning("Failed to connect to server.  Retrying...")
      connect()
  })

  def connected(connection: ActorRef): Receive = 
    register orElse {
      case Client.Output(data) =>
        connection ! Write(data.toByteString)
      case CommandFailed(_: Write) =>
        log.error("Failed to write data to server")
      case Received(data) =>
        var xor = for {
          hd <- HeaderDecoder(data.toByteVector)
          (header, data) = hd
          listener <- listeners.get(header).toRightXor(NoListenerError(header))
        } yield listener ! Client.Incoming(header, data)
       
        xor.leftMap { err => log.error(err.toString()) }
      case _: ConnectionClosed =>
        log.error("Connection between server closed")
        context stop self
    }

  def receive: Receive = unconnected
}

object HeaderDecoder {
  private val decoder = for {
    header <- scodec.codecs.byte
    data <- scodec.codecs.bytes
  } yield (header, data)

  def apply(data: ByteVector): Xor[DecodeError, (Byte, ByteVector)] = 
    decoder.decode(data.toBitVector).toXor.leftMap(DecodeError).map(_.value)
}

case class NoListenerError(header: Byte) extends Throwable

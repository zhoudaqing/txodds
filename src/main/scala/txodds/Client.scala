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

case class Incoming(header: Byte, data: ByteVector)
case class Outgoing(data: ByteVector)

object Client {
  sealed trait Received extends Any
  case class RegisterListener(header: Byte, listener: ActorRef) extends Received
  case object Connected
  
  def props(tcpActor: ActorRef, remote: InetSocketAddress): Props =
    Props(new Client(tcpActor, remote))
}

class Client(tcpActor: ActorRef, remote: InetSocketAddress) extends Actor with ActorLogging {

  val listeners = mu.Map[Byte, ActorRef]()

  override def preStart(): Unit = {
    connect()
  }

  def connect(): Unit = tcpActor ! Connect(remote)


  def unconnected: Receive = {
    case Client.RegisterListener(header, listener) =>
      listeners += (header -> listener)
    case c @ Connected(_, _) =>
      log.info("Connected. Registering client on connection")
      val connection = sender()
      connection ! Register(self)
      listeners.values.toSet.foreach { (l: ActorRef) => l ! Client.Connected }
      context become connected(connection)
    case CommandFailed(_: Connect) =>
      log.warning("Failed to connect to server.  Retrying...")
      connect()
    case o => log.error("unexpected message when unconnected [{}] from [{}]", o, sender().path.name)
  }

  def connected(connection: ActorRef): Receive = {
    case Client.RegisterListener(header, listener) =>
      listeners += (header -> listener)
      listener ! Client.Connected
    case Outgoing(data) =>
      connection ! Write(data.toByteString)
    case CommandFailed(_: Write) =>
      log.error("Failed to write data to server")
    case Received(data) =>
      val (header, bytes) = Codecs.decode(Codecs.headerDecoder, data.toBitVector)
      val l = listeners(header)
      l ! Incoming(header, bytes)
    case _: ConnectionClosed =>
      log.error("Connection between server closed")
      context stop self
    case o => log.error("unexpected message when connected [{}]", o)
  }

  def receive: Receive = unconnected
}

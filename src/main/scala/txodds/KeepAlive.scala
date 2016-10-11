package txodds

import akka.actor._
import akka.io._
import java.net.InetSocketAddress

import scodec._
import scodec.bits._

import cats.data._
import cats.implicits._

import scala.concurrent.duration._

import Headers._
import Codecs._

object KeepAlive {
  case object Send
  case object CheckResponse
  case object Terminate

  def props(timeout: FiniteDuration, period: FiniteDuration, connection: ActorRef, 
    listeners: List[ActorRef]): Props =
    Props(new KeepAlive(timeout, period, connection, listeners))
}

class KeepAlive(timeout: FiniteDuration, period: FiniteDuration, connection: ActorRef, 
  listeners: List[ActorRef])
    extends Actor with ActorLogging {

  implicit val dispatcher = context.system.dispatcher
  var _responseReceived: Boolean = _

  override def preStart(): Unit = {
    log.info("starting keepalive")
    connection ! Client.RegisterListener(keepAliveResponse, self)
    connection ! Client.RegisterListener(keepAliveRequest, self)
  }

  def receive: Receive = initialize

  def initialize: Receive = {
    case Client.Connected =>
      sendKeepAlive
      context become awaitResponse
  }

  def respond: Receive = {
    case Incoming(`keepAliveRequest`, _) =>
      log.info("responding to keepAlive request")
      val data = encode(headerCodec)(keepAliveResponse)
      connection ! Outgoing(data.toByteVector)
  }

  def awaitResponse: Receive = respond orElse {
    case Incoming(`keepAliveResponse`, _) =>
      log.info("received keepalive response")
      _responseReceived = true
    case KeepAlive.CheckResponse =>
      if(_responseReceived) {
        scheduleSendKeepAlive()
      } else {
        listeners.foreach { _ ! KeepAlive.Terminate }
        log.error("Keepalive failed. Exiting application")
        context stop self
      }
  }

  def awaitSend: Receive = respond orElse {
    case KeepAlive.Send =>
      sendKeepAlive()
  }

  private def sendKeepAlive(): Unit = {
    _responseReceived = false

    val data = encode(headerCodec)(keepAliveRequest)
    log.info("sending keepalive")
    connection ! Outgoing(data.toByteVector)
    context.system.scheduler.scheduleOnce(timeout, self, KeepAlive.CheckResponse)
  }

  private def scheduleSendKeepAlive(): Unit = {
    _responseReceived = false
    context.system.scheduler.scheduleOnce(period, self, KeepAlive.Send)
    context become awaitSend
  }
}


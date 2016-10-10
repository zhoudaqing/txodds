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

class KeepAlive(timeout: FiniteDuration, duration: FiniteDuration, connection: ActorRef, 
  listeners: List[ActorRef])
    extends Actor with ActorLogging {

  implicit val dispatcher = context.system.dispatcher
  var _responseReceived: Boolean = _

  override def preStart(): Unit = {
    connection ! Client.RegisterListener(keepAliveResponse, self)
    connection ! Client.RegisterListener(keepAliveRequest, self)
    sendKeepAlive()
  }

  def receive: Receive = awaitResponse

  def respond: Receive = {
    case Client.Incoming(`keepAliveRequest`, _) =>
      val data = headerCodec.encode(keepAliveResponse).toXor
      data.foreach { d => connection ! Client.Output(d.toByteVector) }
      data.leftMap { err => log.error(EncodeError(err).toString()) }
  }

  def awaitResponse: Receive = respond orElse {
    case Client.Incoming(`keepAliveResponse`, _) =>
      _responseReceived = true
    case KeepAlive.CheckResponse =>
      if(_responseReceived) {
        log.debug("received keepalive response")
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
    
    val data = headerCodec.encode(keepAliveRequest).toXor
    data.foreach { d => connection ! Client.Output(d.toByteVector) }
    data.leftMap { err => log.error(EncodeError(err).toString()) }

    context.system.scheduler.scheduleOnce(timeout, self, KeepAlive.CheckResponse)
  }

  private def scheduleSendKeepAlive(): Unit = {
    _responseReceived = false
    context.system.scheduler.scheduleOnce(timeout, self, KeepAlive.Send)
    context become awaitSend
  }
}


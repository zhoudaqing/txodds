package txodds

import akka.actor._
import akka.io._

import cats.data._
import cats.implicits._

import scodec.codecs._

import Headers._
import Codecs._

import scala.concurrent.duration._
import scala.collection.{mutable => mu}

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import scala.io.StdIn

object StatsReporter {
  def props(http: HttpExt, materializer: ActorMaterializer, host: String, port: Int): Props =
    Props(new StatsReporter(http, materializer, host, port))
}

class StatsReporter(http: HttpExt, materializer: ActorMaterializer, host: String, port: Int) extends Actor with ActorLogging {

  implicit val _m: ActorMaterializer = materializer

  var message: String = "Stats not yet present"
  def display: String = s"<p>$message</p>"

  val route =
    path("stats") {
      get {
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, display))
      }
    }

  val bindingFuture = http.bindAndHandle(route, host, port)

  def receive: Receive = {
    case m: String => message = m
  }
}

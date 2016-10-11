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
import scala.collection.{mutable => mu}

import java.net.InetSocketAddress

import Tcp._

object ServerApp {

  def main(args: Array[String]) = {
    implicit val system = ActorSystem("Server")

    val http = Http()
    val materializer = ActorMaterializer()
    val reporter = system.actorOf(StatsReporter.props(http, materializer, "localhost", 8092))
    val database = system.actorOf(Database.props("basic-db", "numbers"), "database")
    val io = IO(Tcp)
    val port = new InetSocketAddress("localhost", 8080)
    val server = system.actorOf(ServerSystem.props(io, port, 5 minutes, 30 minutes, 10, reporter, database))
  }
}

object ServerSystem {
  def props(tcpActor: ActorRef, port: InetSocketAddress, 
    timeout: FiniteDuration, period: FiniteDuration, poolSize: Int, reporter: ActorRef, dbActor: ActorRef): Props = Props(new ServerSystem(tcpActor, 
      port, timeout, period, poolSize, reporter, dbActor))
}

class ServerSystem(tcpActor: ActorRef, port: InetSocketAddress, timeout: FiniteDuration, period: FiniteDuration, poolSize: Int, reporter: ActorRef, dbActor: ActorRef) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    val core = context.actorOf(Core.props(poolSize, reporter, dbActor), "core")
    val f = Handler.props(core, timeout, period) _
    val readerServer = context.actorOf(Server.props(tcpActor, port, f), "server")
  }

  def receive: Receive = {
    case msg => log.error("Unexpected message %s", msg)
  }
}

object Server {
  def props(tcpActor: ActorRef, remote: InetSocketAddress, f: ActorRef => Props): Props = 
    Props(new Server(tcpActor, remote, f))
}

class Server(tcpActor: ActorRef, remote: InetSocketAddress, f: ActorRef => Props) extends Actor with ActorLogging {
     
  import context.system
  
  val listeners = mu.Map[Byte, ActorRef]()

  override def preStart(): Unit = {
    tcpActor ! Bind(self, remote)
  }

  def receive: Receive = {
    case b @ Bound(localAddress) =>
      log.info("Bound to address [{}]", localAddress)
    case c @ Connected(remote, local) =>
      val connection = sender()
      log.info("Connected to actor [{}]", connection.path.name)
      val handler: ActorRef = context.actorOf(f(connection), s"handler-${connection.path.name}")
    case CommandFailed(_: Bind) => 
      log.error("Failed to bind to port. Exiting.")
      context stop self
  }
}


import scodec.bits._

object Handler {
  def props(core: ActorRef, timeout: FiniteDuration, period: FiniteDuration)(connection: ActorRef): Props = 
    Props(new Handler(connection, core, timeout, period))
}

class Handler(connection: ActorRef, core: ActorRef, 
  timeout: FiniteDuration, period: FiniteDuration) extends Actor with ActorLogging {

  var keepAlive: ActorRef = _

  override def preStart(): Unit = {
    connection ! Register(self)
    keepAlive = context.actorOf(KeepAlive.props(timeout, period, self, List(self)), "keepalive")
    keepAlive ! Client.Connected
  }

  def handleKeepAlive: Receive = {
    case _ : Client.RegisterListener =>
    case Outgoing(d) => 
      log.info("propagating keepalive")
      connection ! Write(d.toByteString)
    case KeepAlive.Terminate =>
      log.error("Keepalive failed. Terminating.")
      context stop self
  }

  def handle(header: Byte, data: ByteVector): Unit = header match {
    case `keepAliveRequest` => keepAlive ! Incoming(keepAliveRequest, data)
    case `keepAliveResponse` => keepAlive ! Incoming(keepAliveResponse, data)
    case `writeGreet` =>
      core ! Core.RegisterWriter(connection)
      context become forward
    case `startSequence` =>
      core ! Core.RegisterReader(connection)
      core ! ((header, data))
      context become forward
  }

  def start: Receive = handleKeepAlive orElse {
    case Received(data) =>
      val (header, bytes) = decode(headerDecoder, data.toBitVector)
      handle(header, bytes)
  }

  def forward: Receive = handleKeepAlive orElse {
    case Received(data) =>
      val (header, bytes) = decode(headerDecoder, data.toBitVector)
      header match {
        case `keepAliveRequest` => keepAlive ! Incoming(keepAliveRequest, bytes)
        case `keepAliveResponse` => keepAlive ! Incoming(keepAliveResponse, bytes)
        case _ => 
          log.info("forwarding message with header [{}] onto core system", header)
          core ! ((header, bytes))
      }
    case PeerClosed => context stop self
  }

  def receive: Receive = start
}

import java.util.UUID
import scala.collection.immutable._
import scala.util.Random

object Core {
  case class RegisterWriter(writer: ActorRef)
  case class RegisterReader(reader: ActorRef)

  sealed trait State
  object Uninitialized extends State
  object Initialized extends State

  sealed trait Data
  case class PendingClients(reader: Option[ActorRef], writer: Option[ActorRef], waiting: Queue[UUID]) 
      extends Data

  case class Clients(reader: ActorRef, writer: ActorRef)
  case class Sequences(all: Queue[Queue[Int]], current: Queue[Int], remaining: Int)
  case class Running(clients: Clients, sequences: Sequences, waiting: Queue[UUID], 
    paired: Map[UUID, Queue[Int]]) extends Data

  def props(size: Int, reporter: ActorRef, database: ActorRef): Props = Props(new Core(size, reporter, database))
}

class Core(poolSize: Int, reporter: ActorRef, database: ActorRef) extends Actor with FSM[Core.State, Core.Data] {
  import Core._

  val random = new Random()

  when(Uninitialized) {
    case Event(RegisterWriter(w), PendingClients(None, _, q)) =>
      log.info("Registered writer. Waiting for reader.")
      stay using PendingClients(None, Some(w), q)
    case Event(RegisterWriter(w), PendingClients(Some(r), _, q)) =>
      log.info("Registered writer. Initialization complete.")
      val size = requestSequence(w)
      val sequences = Sequences(Queue.empty, Queue.empty, size)
      goto(Initialized) using Running(Clients(r, w), sequences, q, Map.empty)
    case Event(RegisterReader(r), PendingClients(_, None, q)) =>
      log.info("Registered reader. Waiting for writer.")
      stay using PendingClients(Some(r), None, q)
    case Event(RegisterReader(r), PendingClients(_, Some(w), q)) =>
      log.info("Registered reader. Initialization complete.")
      val size = requestSequence(w)
      val sequences = Sequences(Queue.empty, Queue.empty, size)
      goto(Initialized) using Running(Clients(r, w), sequences, q, Map.empty)
    case Event((`startSequence`, data: ByteVector), PendingClients(r, w, q)) =>
      val uuid = decode(uuidCodec, data.toBitVector)
      stay using PendingClients(r, w, q.enqueue(uuid))
  }

  def requestSequence(writer: ActorRef): Int = {
    val offset = random.nextInt(100)
    val size = random.nextInt(9) + 1
    log.info("requesting sequence with offset [{}] size [{}] from writer", offset, size)
    val data = encode(headerCodec ~ sequenceRequestCodec)((writeSequenceRequest, SequenceRequest(offset, size)))
    writer ! Write(data.toByteVector.toByteString)
    size
  }

  when(Initialized) {
    case Event((`writeSequenceResponse`, data: ByteVector), r: Running) => 
      val i = decode(int32, data.toBitVector)
      database ! i
      log.info("received number [{}] - awaiting [{}] numbers", i, r.sequences.remaining - 1)
      val sequences = if(r.sequences.remaining > 1)
        Sequences(r.sequences.all, r.sequences.current.enqueue(i), r.sequences.remaining - 1)
      else {
        log.info("received full sequence from writer")
        val remaining = requestSequence(r.clients.writer)
        Sequences(r.sequences.all.enqueue(r.sequences.current.enqueue(i)), Queue.empty, remaining)
      }
      val rr = Running(r.clients, sequences, r.waiting, r.paired)
      report(rr)
      stay using queueSequence(rr)
    case Event((`startSequence`, data: ByteVector), r: Running) =>
      log.info("received start sequence request")
      val id = decode(startSequenceCodec, data.toBitVector)
      val rr = Running(r.clients, r.sequences, r.waiting.enqueue(id), r.paired)
      report(rr)
      stay using queueSequence(rr)
    case Event((`nextNumberRequest`, data: ByteVector), r: Running) => 
      val id = decode(uuidCodec, data.toBitVector)
      val paired = sendNumber(r.clients.reader, id, r.paired(id), r.paired)
      val rr = r.copy(paired = paired)
      report(rr)
      stay using rr
    case Event((`endOfSequenceConfirm`, data), r: Running) => 
      stay using queueSequence(r)
  }

  def queueSequence(r: Running): Running = {
    if(r.waiting.nonEmpty && r.sequences.all.nonEmpty && r.paired.size < poolSize) {
      val (id, nextWaiting) = r.waiting.dequeue
      log.info("dequeueing sequence with id [{}]", id)
      val (seq, nextSeq) = r.sequences.all.dequeue
      log.info("paired with sequence [{}]", seq)
      val paired = sendNumber(r.clients.reader, id, seq, r.paired)
      Running(r.clients, Sequences(nextSeq, r.sequences.current, r.sequences.remaining), nextWaiting, paired)
    } else {
      Running(r.clients, r.sequences, r.waiting, r.paired)
    }
  }

  def sendNumber(reader: ActorRef, id: UUID, numbers: Queue[Int], pairs: Map[UUID, Queue[Int]]): 
      Map[UUID, Queue[Int]] = {
    if(numbers.nonEmpty) {
      val (i, next) = numbers.dequeue
      reader ! Write(encode(headerCodec ~ nextNumberCodec)((nextNumber, NextNumber(id, i))).toByteVector.toByteString)
      pairs + ((id, next))
    } else {
      val data = encode(headerCodec ~ endOfSequenceCodec)((endOfSequence, id))
      reader ! Write(data.toByteVector.toByteString)
      pairs - id
    }
  }

  def report(r: Running): Unit = {
    reporter ! s"open pairs [${r.paired.size}] pending ids [${r.waiting.size}] pending sequences [${r.sequences.all.size}] "
  }

  startWith(Uninitialized, PendingClients(None, None, Queue.empty))
}

case class SequenceRequest(offset: Int, size: Int)
case class NextNumber(uuid: UUID, number: Int)

package txodds

import akka.actor._
import akka.io._
import java.net.InetSocketAddress

import scodec._
import scodec.bits._

import cats.data._
import cats.implicits._

import scala.concurrent.duration._

import org.mongodb.scala._
import org.mongodb.scala.connection._
import scala.collection.JavaConverters._

object Database {
  def props(dbName: String, colName: String): Props = Props(new Database(dbName, colName))
}

class Database(dbName: String, collectionName: String) extends Actor with ActorLogging {

  val clusterSettings: ClusterSettings = ClusterSettings.builder().hosts(List(new ServerAddress("localhost")).asJava).build()
  val settings: MongoClientSettings = MongoClientSettings.builder().clusterSettings(clusterSettings).build()
  val mongoClient: MongoClient = MongoClient(settings)

  val database: MongoDatabase = mongoClient.getDatabase(dbName)
  val collection = database.getCollection(collectionName)

  def receive: Receive = {
    case number: Int =>
      collection.insertOne(Document({ "number" -> number }))
  }
}

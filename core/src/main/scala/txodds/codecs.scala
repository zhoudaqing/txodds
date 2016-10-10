package txodds

import akka.actor._

import scodec._
import scodec.codecs._
import scodec.bits._

import java.util.UUID

case class SequenceRequest(offset: Int, size: Int)
case class NextNumber(uuid: UUID, number: Int)

object Headers {
  /** ping a system */
  val keepAliveRequest: Byte = 0
  /** respond to keepalive ping */
  val keepAliveResponse: Byte = 1
  /** from Server to Writer - writer should write a new sequence */
  val writeSequenceRequest: Byte = 2
  /** from Writer to Server - writer has written a number in the sequence */
  val writeSequenceResponse: Byte = 3
  /** from Reader to Server - start sending numbers for a given UUID */
  val startSequence: Byte = 4
  /** fron Server to Reader - sends a number in a sequence for a given UUID */
  val nextNumber: Byte = 5
  /** from Reader to Server - request another number for a given UUID */
  val nextNumberRequest: Byte = 6
  /** from Server to Reader - server has finished sending numbers for a given UUID */
  val endOfSequence: Byte = 7
}

object Codecs {

  val headerCodec: Codec[Byte] = byte

  val sequenceRequestCodec: Codec[SequenceRequest] = (int32 ~ int32).xmap[SequenceRequest](
    { case (o, s) => SequenceRequest(o, s)}, s => (s.offset, s.size))

  val writeNumberCodec: Codec[Int] = int32
  val uuidCodec: Codec[UUID] = (int64 ~ int64).xmap({
    case (lsb, msb) => new UUID(lsb, msb) }, 
    uuid => (uuid.getLeastSignificantBits, uuid.getMostSignificantBits))

  val nextNumberRequestCodec: Codec[UUID] = uuidCodec
  val nextNumberResponseCodec: Codec[NextNumber] = (uuidCodec ~ int32).xmap({
    case (uuid, num) => NextNumber(uuid, num) }, 
    nn => (nn.uuid, nn.number))

  val endOfSequenceCodec: Codec[UUID] = (uuidCodec ~ int32).exmap({
    case (uuid, num) => if(num == -1) 
      Attempt.Successful(uuid) else
      Attempt.Failure(Err("Unable to decode end of sequence"))
  }, uuid => Attempt.Successful((uuid, -1)))

  val startSequenceCodec: Codec[UUID] = (uuidCodec ~ int32).exmap({
    case (uuid, num) => if(num == 0)
      Attempt.Successful(uuid) else
      Attempt.Failure(Err("Unable to decode end of sequence"))
  }, uuid => Attempt.Successful((uuid, 0)))
}

case class DecodeError(err: Err) extends Throwable
case class EncodeError(err: Err) extends Throwable

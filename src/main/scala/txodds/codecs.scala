package txodds

import akka.actor._

import scodec._
import scodec.codecs._
import scodec.bits._

import java.util.UUID

import cats.data._

object Headers {
  // ping a system
  val keepAliveRequest: Byte = 0
  // respond to keepalive ping
  val keepAliveResponse: Byte = 1

  // from Server to Writer - writer should write a new sequence
  val writeSequenceRequest: Byte = 2
  // from Writer to Server - writer has written a number in the sequence
  val writeSequenceResponse: Byte = 3
  // from Reader to Server - start sending numbers for a given UUID
  val startSequence: Byte = 4
  // from Server to Reader - sends a number in a sequence for a given UUID
  val nextNumber: Byte = 5
  // from Reader to Server - request another number for a given UUID
  val nextNumberRequest: Byte = 6
  // from Server to Reader - server has finished sending numbers for a given UUID
  val endOfSequence: Byte = 7
  //from the Reader to the Server - confirms the sequence has ended
  val endOfSequenceConfirm: Byte = 8

  //from Writer to Server - greet message
  val writeGreet: Byte = 9
}

object Codecs {

  val headerDecoder: Decoder[(Byte, ByteVector)] = for {
    header <- scodec.codecs.byte
    data <- scodec.codecs.bytes
  } yield (header, data)

  def encode[A](codec: Codec[A])(a: A): BitVector =
    codec.encode(a).toXor match {
      case Xor.Left(err) => sys.error(err.toString())
      case Xor.Right(b) => b
    }

  def decode[A](codec: Decoder[A], bv: BitVector): A = codec.decode(bv).toXor match {
    case Xor.Left(err) => sys.error(err.toString())
    case Xor.Right(res) => res.value
  }

  val headerCodec: Codec[Byte] = byte

  val sequenceRequestCodec: Codec[SequenceRequest] = (int32 ~ int32).xmap[SequenceRequest](
    { case (o, s) => SequenceRequest(o, s)}, s => (s.offset, s.size))

  val uuidCodec: Codec[UUID] = (int64 ~ int64).xmap({
    case (lsb, msb) => new UUID(lsb, msb) }, 
    uuid => (uuid.getLeastSignificantBits, uuid.getMostSignificantBits))

  val nextNumberCodec: Codec[NextNumber] = (uuidCodec ~ int32).xmap({
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
      Attempt.Failure(Err("Unable to decode start sequence"))
  }, uuid => Attempt.Successful((uuid, 0)))
}

package com.dominikgruber.scalatorrent.peerwireprotocol.network

import java.nio.ByteBuffer

import akka.util.ByteString
import com.dominikgruber.scalatorrent.peerwireprotocol.message._
import com.dominikgruber.scalatorrent.peerwireprotocol.network.MessageBuffer._

import scala.collection.mutable

class MessageBuffer {

  private val buffer: mutable.ArrayBuffer[Byte] = mutable.ArrayBuffer.empty

 /**
    * @return a message; or
    *         multiple messages received at once; or
    *         no messages if just a part was received.
    *
    *         maybe accompanied by some "rubbish" bytes we don't understand.
    */
  def receiveBytes[M <: MessageOrHandshake](mode: Mode[M])(newBytes: ByteString): Result[M] = {

    def parseMessages: Result[M] =
      parseNextMessage match {
        case Right(msg) => msg +: parseMessages
        case Left(rubbish) => Result(rubbish)
      }

    /**
      * @return a full, parsed message; or
      *         a Left with "rubbish" (bytes we don't understand); or
      *         a Left with an empty vector meaning the buffer is empty or has an incomplete (but still valid) message
      */
    def parseNextMessage: RubbishOr[M] =
      nextRawMessage match {
        case Right(bytes) => mode.parse(bytes).toRight(discard(bytes))
        case Left(Invalid) => Left(discard(Vector.empty))
        case Left(Incomplete) => Left(Vector.empty)
      }

    def discard(bytes: Bytes): Bytes = {
      val rubbish = bytes ++ buffer.toVector
      buffer.clear() //flush the whole buffer, hoping to receive the beginning of a valid message next.
      rubbish
    }

    /**
      * Consume the next complete message from the buffer.
      */
    def nextRawMessage: Either[NoResult, Bytes] =
      messageLength.right.flatMap { len =>
        if(buffer.size < len){
          Left(Incomplete)
        } else {
          val result = buffer.take(len).toVector
          buffer.trimStart(len)
          Right(result)
        }
      }

    /**
      * Expected length for the current message (maybe partially) in the buffer.
      */
    def messageLength: Either[NoResult, Int] = {
      if(buffer.nonEmpty && buffer.head < 0)
        Left(Invalid)
      else if(buffer.size < mode.headerLength)
        Left(Incomplete)
      else {
        val header = buffer.take(mode.headerLength).toArray
        val length = parseInt(header) + mode.fixedLength
        Right(length)
      }
    }

    def parseInt(bytes: Array[Byte]): Int = {
      val zeroPadding = Array.fill(4)(0.toByte)
      val fourBytes = (zeroPadding ++ bytes).takeRight(4)
      ByteBuffer.wrap(fourBytes).getInt
    }

    buffer ++= newBytes.toArray
    parseMessages
  }
}

object MessageBuffer {

  private type Bytes = Vector[Byte]
  private type RubbishOr[M] = Either[Bytes, M]

  private sealed trait NoResult
  private case object Incomplete extends NoResult
  private case object Invalid extends NoResult

  case class Mode[M <: MessageOrHandshake](
    parse: Bytes => Option[M],
    headerLength: Int,
    fixedLength: Int
  )
  val MessageMode: Mode[Message] = Mode(Message.unmarshal, 4, 4)
  val HandshakeMode: Mode[Handshake] = Mode(Handshake.unmarshal, 1, 49)

  case class Result[M <: MessageOrHandshake](messages: List[M], rubbish: Option[Bytes]) {
    def +:(message: M): Result[M] = Result(message +: messages, rubbish)
  }
  object Result {
    def apply[M <: MessageOrHandshake](messages: List[M]): Result[M] = Result(messages, None)
    def apply[M <: MessageOrHandshake](rubbish: Bytes): Result[M] =
      if(rubbish.isEmpty) Result(Nil, None) //"empty rubbish" is just the end of the buffer
      else Result(Nil, Some(rubbish))
  }
}

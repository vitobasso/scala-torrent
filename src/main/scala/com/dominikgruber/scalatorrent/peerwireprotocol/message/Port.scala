package com.dominikgruber.scalatorrent.peerwireprotocol.message

import java.nio.ByteBuffer

import akka.util.ByteStringBuilder

case class Port(port: Short) extends Message {

  override def lengthPrefix = 3

  override def messageId: Option[Byte] = Some(Port.MESSAGE_ID)

  override def marshal: Vector[Byte] = {
    val bsb = new ByteStringBuilder()
    bsb.putBytes(super.marshal.toArray)
    bsb.putShort(port)
    bsb.result().toVector
  }
}

object Port {

  val MESSAGE_ID: Byte = 9

  def unmarshal(message: Vector[Byte]): Option[Port] = {
    if (message.length == 7 && message(4) == MESSAGE_ID) {
      val l = ByteBuffer.wrap(message.slice(0, 4).toArray).getInt
      if (l == 3) {
        val pieceIndex = ByteBuffer.wrap(message.slice(5, 7).toArray).getShort
        return Some(Port(pieceIndex))
      }
    }
    None
  }

}
package com.dominikgruber.scalatorrent.peerwireprotocol

import akka.util.ByteStringBuilder
import java.nio.ByteOrder

/**
 * All of the messages besides the Handshake take the form of
 * <length prefix><message ID><payload>. The length prefix is a four byte
 * big-endian value. The message ID is a single decimal byte. The payload is
 * message dependent.
 */
abstract class Message {
  def lengthPrefix: Int
  def messageId: Option[Byte]

  protected implicit val byteOrder = ByteOrder.BIG_ENDIAN

  def marshal: Vector[Byte] = {
    val bsb = new ByteStringBuilder()
    bsb.putInt(lengthPrefix)
    if (messageId.isDefined) bsb.putByte(messageId.get)
    bsb.result().toVector
  }
}

object Message {

  def unmarshal(message: Vector[Byte]): Option[Message] = ???
}
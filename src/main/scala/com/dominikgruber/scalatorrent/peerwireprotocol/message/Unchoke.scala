package com.dominikgruber.scalatorrent.peerwireprotocol.message

/**
 * unchoke: <len=0001><id=1>
 * The unchoke message is fixed-length and has no payload.
 */
case class Unchoke() extends Message {
  override def lengthPrefix = 1
  override def messageId = Some(Unchoke.MESSAGE_ID)
}

object Unchoke {

  val MESSAGE_ID: Byte = 1

  def unmarshal(message: Vector[Byte]): Option[Unchoke] = {
    if (message == Unchoke().marshal) Some(Unchoke())
    else None
  }
}
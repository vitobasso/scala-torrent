package com.dominikgruber.scalatorrent.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Stash}
import akka.io.Tcp.{PeerClosed, Received, Write}
import akka.util.ByteString
import com.dominikgruber.scalatorrent.actor.PeerConnection.SetListener
import com.dominikgruber.scalatorrent.actor.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.actor.ToByteString._
import com.dominikgruber.scalatorrent.peerwireprotocol.{Handshake, Message, MessageOrHandshake}
import com.dominikgruber.scalatorrent.transfer.MessageBuffer
import com.dominikgruber.scalatorrent.transfer.MessageBuffer.{HandshakeMode, MessageMode, Mode}

import scala.collection.mutable.ArrayBuffer

object PeerConnection {
  case class SetListener(listener: ActorRef)
}

class PeerConnection(tcp: ActorRef)
  extends Actor with ActorLogging with Stash {

  val buffer = new MessageBuffer

  override def receive: Receive = {
    case SetListener(listener) => // from Coordinator
      unstashAll()
      context become handshaking(listener)
    case _ => stash()
  }

  def handshaking(listener: ActorRef): Receive =
    behavior(HandshakeMode, listener)
      .orElse {
        case SetListener(newListener) => // from Torrent
          context become sharing(newListener)
      }

  def sharing(listener: ActorRef): Receive =
    behavior(MessageMode, listener)

  def behavior[M <: MessageOrHandshake](mode: Mode[M], listener: ActorRef): Receive = {

    case Received(data) => // from Tcp
      val result = buffer.receiveBytes(mode)(data)
      result.messages.foreach { message =>
        log.debug(s"Received $message")
        listener ! message
      }
      result.rubbish.foreach { bytes =>
        log.warning(s"Received unknown ${bytes.size}b message: ${Hex(bytes.take(15))} ...")
      }

    case SendToPeer(msg: MessageOrHandshake) => // from PeerSharing
      log.debug(s"Sending $msg")
      tcp ! Write(msg)

    case PeerClosed => // from Tcp
      log.warning("Peer closed")

  }

}

object ToByteString {
  implicit def messageOrHandshakeToBytes(messageOrHandshake: MessageOrHandshake): ByteString =
    messageOrHandshake match {
      case m: Message => messageToBytes(m)
      case h: Handshake => handshakeToBytes(h)
    }
  implicit def handshakeToBytes(handshake: Handshake): ByteString =
    ByteString(handshake.marshal.toArray)
  implicit def messageToBytes(message: Message): ByteString =
    ByteString(message.marshal.toArray)
}

object Hex {
  def apply(buf: Array[Byte]): String = buf.map("%02X" format _).mkString(" ")
  def apply(buf: Vector[Byte]): String = apply(buf.toArray)
  def apply(buf: ArrayBuffer[Byte]): String = apply(buf.toArray)
  def apply(buf: ByteString): String = apply(buf.toArray)
}

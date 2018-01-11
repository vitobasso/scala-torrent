package com.dominikgruber.scalatorrent.peerwireprotocol.network

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}
import akka.io.Tcp._
import akka.util.ByteString
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.peerwireprotocol.message.{Handshake, Message, MessageOrHandshake}
import com.dominikgruber.scalatorrent.peerwireprotocol.network.MessageBuffer._
import com.dominikgruber.scalatorrent.peerwireprotocol.network.PeerConnection.SetListener
import com.dominikgruber.scalatorrent.peerwireprotocol.network.ToByteString._
import com.dominikgruber.scalatorrent.util.ByteUtil.Hex

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
    exchanging(HandshakeMode, listener) orElse
      switchingListener orElse
      unexpected

  def sharing(listener: ActorRef): Receive =
    exchanging(MessageMode, listener) orElse
      unexpected

  def exchanging[M <: MessageOrHandshake](mode: Mode[M], listener: ActorRef): Receive = {

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

    case c: ConnectionClosed => // from Tcp
      log.warning(s"Connection closed: $c")
      listener ! PeerConnection.Closed
  }

  def switchingListener: Receive = {
    case SetListener(newListener) => // from Torrent
      context become sharing(newListener)
  }

  def unexpected: Receive = {
    case unexpected =>
      log.warning(s"Unexpected event: $unexpected")
  }

  override def postStop(): Unit = {
    tcp ! Abort
  }

}

object PeerConnection {
  def props(tcp: ActorRef) = Props(classOf[PeerConnection], tcp)

  case class SetListener(listener: ActorRef)
  case object Closed
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

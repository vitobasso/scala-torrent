package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.ISO_8859_1

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.io.Udp._
import akka.io.{IO, Udp}
import akka.util.ByteString
import com.dominikgruber.scalatorrent.dht.message.DhtMessage.Message
import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}
import com.dominikgruber.scalatorrent.dht.message.KrpcEncoding
import com.dominikgruber.scalatorrent.dht.message.ShortDescription.show

object UdpSocket {
  case class SendToNode(message: Message, remote: InetSocketAddress)
  case class ReceivedFromNode(message: Message, remote: InetSocketAddress)
}

case class UdpSocket(listener: ActorRef, port: Int) extends Actor with ActorLogging {

  //TODO tolerate akka://scala-torrent/system/IO-UDP-FF/selectors/$a/0 - Can't assign requested address
  val udpManager: ActorRef = IO(Udp)(context.system)

  override def preStart(): Unit = {
    bind()
  }

  override def receive: Receive = binding

  def binding: Receive = {
    case Bound(local) =>
      val socket = sender()
      context.watch(socket)
      context.become(ready(socket))
    case other =>
  }

  def ready(socket: ActorRef): Receive = {
    case Received(data, remote) =>
      receive(data, remote)
    case SendToNode(msg, remote) =>
      send(socket, msg, remote)
    case Unbind => socket ! Unbind
    case Unbound => context.stop(self)
    case Terminated(actor) =>
      log.warning(s"Trying to reconnect to recover from failure in ${actor.path}")
      context.become(binding)
      bind()
  }

  def receive(data: ByteString, remote: InetSocketAddress): Unit = {
    val str = data.decodeString(ISO_8859_1)
    KrpcEncoding.decode(str) match {
      case Right(msg) =>
        log.debug(s"Received: ${show(msg)}")
        listener ! ReceivedFromNode(msg, remote)
      case Left(err) =>
        log.error(s"Failed to parse message: $err. $str")
    }
  }

  def send(socket: ActorRef, message: Message, remote: InetSocketAddress): Unit = {
    KrpcEncoding.encode(message) match {
      case Right(str) =>
        log.debug(s"Sending: ${show(message)}")
        socket ! Send(ByteString(str, ISO_8859_1), remote) //TODO a test requiring the charset
      case Left(err) =>
        log.error(s"Failed to send message: $err")
    }
  }

  def bind(): Unit = udpManager ! Bind(self, new InetSocketAddress("0.0.0.0", port))

}

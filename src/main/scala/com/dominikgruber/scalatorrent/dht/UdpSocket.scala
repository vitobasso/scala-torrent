package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.ISO_8859_1

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.Udp._
import akka.io.{IO, Udp}
import akka.util.ByteString
import com.dominikgruber.scalatorrent.dht.message.DhtMessage.Message
import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}
import com.dominikgruber.scalatorrent.dht.message.KrpcEncoding

object UdpSocket {
  case class SendToNode(message: Message, remote: InetSocketAddress)
  case class ReceivedFromNode(message: Message, remote: InetSocketAddress)
}

case class UdpSocket(listener: ActorRef, port: Int) extends Actor with ActorLogging {

  println(s"*** Udp $port, contructor")
  val udpManager: ActorRef = IO(Udp)(context.system)

  override def preStart(): Unit = {
    println(s"*** Udp $port, preStart")
    udpManager ! Bind(self, new InetSocketAddress("0.0.0.0", port))
  }

  def receive: Receive = {
    case Bound(local) =>
      println(s"*** Udp $port, Bound: $local")
      context.become(ready(sender()))
    case other =>
      println(s"*** Udp $port, other: $other")
  }

  def ready(socket: ActorRef): Receive = {
    case Received(data, remote) =>
      val str = data.decodeString(ISO_8859_1)
      println(s"*** Udp $port, Received $str")
      KrpcEncoding.decode(str) match {
        case Right(msg) =>
          log.debug(s"Received: $msg")
          listener ! ReceivedFromNode(msg, remote)
        case Left(err) =>
          log.error(s"Failed to parse message: $err. $str")
      }

    case SendToNode(msg, remote) =>
      println(s"*** Udp $port, Send $msg")
      println(s"*** Udp $port, remote = $remote")
      KrpcEncoding.encode(msg) match {
        case Right(str) =>
          log.debug(s"Sending: $msg")
          socket ! Send(ByteString(str), remote)
        case Left(err) =>
          log.error(s"Failed to send message: $err")
      }

    case other => println(s"*** Udp $port, other: $other")

    case Unbind => socket ! Unbind
    case Unbound => context.stop(self)
  }

}

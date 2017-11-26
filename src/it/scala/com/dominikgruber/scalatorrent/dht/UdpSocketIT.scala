package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.ISO_8859_1

import akka.actor.{ActorRef, Props}
import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}
import com.dominikgruber.scalatorrent.dht.message.DhtMessage.{NodeId, Ping, Pong, TransactionId}
import com.dominikgruber.scalatorrent.util.ActorIT
import com.dominikgruber.scalatorrent.util.ByteUtil.{Bytes, bytes}

import scala.concurrent.duration._

class UdpSocketIT extends ActorIT {

  def createUdpActor(port: Int): ActorRef = {
    def f = new UdpSocket(testActor, port)
    system.actorOf(Props(f), s"udp-socket-$port")
  }

  val localActor = createUdpActor(50001)
  val localAddr = new InetSocketAddress("localhost", 50001)

  val localNode = node("01")
  val transactionId = TransactionId("t-id")

  "Udp actor" should {

    "send a msg locally" in {
      val ping = Ping(transactionId, localNode)
      localActor ! SendToNode(ping, localAddr)

      fishForMessage(5.seconds){
        case ReceivedFromNode(msg: Ping, `localAddr`) => true
        case other => false
      }
    }

    "send a msg remotely" in {
//      val remote = new InetSocketAddress("62.138.0.158", 6969)
//      val remote = new InetSocketAddress("87.233.192.220", 6969)
      val remote = new InetSocketAddress("101.184.125.243", 58291)
      val ping = Ping(transactionId, localNode)
      localActor ! SendToNode(ping, remote)

      fishForMessage(10.seconds){
        case ReceivedFromNode(msg: Pong, remote) => true
        case other => false
      }
    }

  }

  /**
    * Quick node id with the most significant bit defined followed with zeroes
    */
  def node(hexByte: String): NodeId = {
    require(hexByte.length == 2)
    val b: Bytes = bytes(hexByte) ++ Array.fill(19)(0.toByte)
    val str: String = new String(b, ISO_8859_1)
    NodeId.validate(str).right.get
  }

}

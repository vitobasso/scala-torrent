package com.dominikgruber.scalatorrent.peerwireprotocol.network

import java.net.InetSocketAddress

import akka.actor.{ActorRef, PoisonPill, Props}
import akka.io.Tcp
import akka.io.Tcp._
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.peerwireprotocol.message.{Interested, Message}
import com.dominikgruber.scalatorrent.peerwireprotocol.network.ConnectionManager.CreateConnection
import com.dominikgruber.scalatorrent.peerwireprotocol.network.PeerConnection.SetListener
import com.dominikgruber.scalatorrent.tracker.Peer
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}
import scala.concurrent.duration._

class ConnectionManagerSpec extends ActorSpec {
  outer =>

  val ourHost = "localhost"
  val theirHost = "remote"
  val ourPort = 123
  val theirPort = 456
  val ourAddress = new InetSocketAddress(ourHost, ourPort)
  val theirAddress = new InetSocketAddress(theirHost, theirPort)
  val meta: MetaInfo = Mocks.metaInfo()
  val peerId = "peer-id-has-20-chars"
  val peer = Peer(Some(peerId), theirHost, theirPort)
  val coordinator = TestProbe("coordinator")
  val tcpManager = TestProbe("tcp-manager")
  val tcpConn = TestProbe("tcp-connection")

  val connManager: ActorRef = {
    val config = ConnectionManager.Config(123, 0.seconds)
    def createActor = new ConnectionManager(config) {
      override val coordinator: ActorRef = outer.coordinator.ref
      override val tcpManager: ActorRef = outer.tcpManager.ref
    }
    system.actorOf(Props(createActor), "connection-manager")
  }

  "for an inbound connection" must {

    "listen to tcp port" in {
      val Tcp.Bind(handler, addr, _, _, _) =
        tcpManager.expectMsgType[Tcp.Bind]
      handler shouldBe connManager
      addr.getHostName shouldBe "localhost"
      addr.getPort shouldBe 123
    }

    "create a PeerConnection" in {
      tcpConn.send(connManager, Connected(theirAddress, ourAddress))

      val Register(handler, _, _) = tcpConn.expectMsgType[Register]
      val ConnectionManager.Connected(peerConn, addr) = coordinator.expectMsgType[ConnectionManager.Connected]
      peerConn shouldBe handler
      addr.ip shouldBe theirHost
      addr.port shouldBe theirPort
      peerConnShouldWriteToTcp(peerConn)

      cleanup(peerConn)
    }

  }

  "for an outbound connection" must {

    "tell TcpManager to open a connection" in {
      connManager ! CreateConnection(theirAddress)

      val Connect(addr, _, _, _, _) = tcpManager.expectMsgType[Connect]
      addr shouldBe theirAddress
    }

    "create a PeerConnection" in {
      val connRequest = tcpManager.lastSender
      tcpConn.send(connRequest, Connected(theirAddress, ourAddress))

      val Register(handler, _, _) = tcpConn.expectMsgType[Register]
      val ConnectionManager.Connected(peerConn, addr2) = expectMsgType[ConnectionManager.Connected]
      peerConn shouldBe handler
      addr2.ip shouldBe theirHost
      addr2.port shouldBe theirPort
      peerConnShouldWriteToTcp(peerConn)

      cleanup(peerConn)
    }

  }

  def peerConnShouldWriteToTcp(peerConn: ActorRef): Unit = {
    val msg = Interested()
    peerConn ! SetListener(testActor)
    peerConn ! SendToPeer(msg)

    val Write(bytes, _) = tcpConn.expectMsgType[Write]
    Message.unmarshal(bytes.toVector) shouldBe Some(msg)
  }

  def cleanup(peerConn: ActorRef): Unit = {
    peerConn ! PoisonPill
    tcpConn.expectMsg(Abort)
  }

}

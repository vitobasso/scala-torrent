package com.dominikgruber.scalatorrent.peerwireprotocol.network

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import com.dominikgruber.scalatorrent.Coordinator.PeerConnected
import com.dominikgruber.scalatorrent.peerwireprotocol.network.ConnectionManager.CreateConnection
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.util.{Asking, ExtraPattern}

object ConnectionManager {
  case class CreateConnection(remoteAddress: InetSocketAddress)
}

class ConnectionManager(portIn: Int)
  extends Actor with ActorLogging with Asking {

  val coordinator: ActorRef = context.parent
  val tcpManager: ActorRef = IO(Tcp)(context.system)

  override def preStart(): Unit = {
    val endpoint = new InetSocketAddress("localhost", portIn)
    tcpManager ! Tcp.Bind(self, endpoint)
  }

  override def receive = {
    case CreateConnection(remoteAddress) => // from Coordinator.PeerConnRequestActor
      createConnRequestTempActor(remoteAddress, sender)

    case Connected(remoteAddress, _) => // inbound, from Tcp connection
      val tcpConn = sender
      val peerConn = createPeerConnection(remoteAddress, tcpConn)
      tcpConn ! Register(peerConn)
      coordinator ! PeerConnected(peerConn, remoteAddress)
  }

  private def createPeerConnection(address: PeerAddress, tcpConn: ActorRef): ActorRef = {
    val props = Props(classOf[PeerConnection], tcpConn)
    context.actorOf(props, s"peer-connection-$address")
  }

  private def createConnRequestTempActor(remoteAddress: InetSocketAddress, originalSender: ActorRef): ActorRef = {
    val props = Props(new ConnectionRequestActor(remoteAddress, originalSender))
    context.actorOf(props, s"temp-connection-request-${remoteAddress: PeerAddress}")
  }

  class ConnectionRequestActor(remoteAddress: InetSocketAddress, originalSender: ActorRef)
    extends Actor with ActorLogging with ExtraPattern {
    tcpManager ! Connect(remoteAddress)
    override def receive: Receive = {
      case Connected(_, _) => // outbound, from Tcp connection
        val tcpConn = sender
        val peerConn = createPeerConnection(remoteAddress, tcpConn)
        tcpConn ! Register(peerConn)
        originalSender ! PeerConnected(peerConn, remoteAddress)
        done()
    }
  }

}

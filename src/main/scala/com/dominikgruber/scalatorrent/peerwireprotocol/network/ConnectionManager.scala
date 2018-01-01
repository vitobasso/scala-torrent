package com.dominikgruber.scalatorrent.peerwireprotocol.network

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.util.{Asking, ExtraPattern}

object ConnectionManager {
  case class CreateConnection(remoteAddress: InetSocketAddress)
  case class Connected(peerConn: ActorRef, address: PeerAddress)
  case class Failed(message: Option[Throwable])
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
    case ConnectionManager.CreateConnection(remoteAddress) => // from Coordinator.PeerConnRequestActor
      createConnRequestTempActor(remoteAddress, sender)
      logPeerConnCounts()

    case Tcp.Connected(remoteAddress, _) => // inbound, from Tcp connection
      log.info(s"Inbound peer connection received from ${remoteAddress.getHostName}")
      val tcpConn = sender
      val peerConn = createPeerConnection(remoteAddress, tcpConn)
      tcpConn ! Register(peerConn)
      coordinator ! ConnectionManager.Connected(peerConn, remoteAddress)
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
      case Tcp.Connected(addr, _) => // outbound, from Tcp connection
        log.debug(s"TCP connected: $addr")
        val tcpConn = sender
        val peerConn = createPeerConnection(remoteAddress, tcpConn)
        tcpConn ! Tcp.Register(peerConn)
        originalSender ! ConnectionManager.Connected(peerConn, remoteAddress)
        done()
      case f @ Tcp.CommandFailed(cmd: Connect) if cmd.remoteAddress == remoteAddress =>
        originalSender ! ConnectionManager.Failed(f.cause)
        log.warning(s"Failed to connect to $remoteAddress. Cause: ${f.cause}")
        done()
      case unknown =>
        log.warning(s"Unknown message: $unknown")
        done()
    }
  }

  private def logPeerConnCounts(): Unit = {

    def status(actorName: String): String =
      actorName match {
        case s: String if s.startsWith("peer-connection") => "active"
        case s: String if s.startsWith("temp-connection-request") => "connecting"
        case _ => "unknown"
      }

    val childCounts: String = context.children
      .map(_.path.name).map(status)
      .groupBy(identity).mapValues(_.size)
      .map{ case (k, v) => s"$v $k" }.mkString(", ")

    log.info(s"peer connections: $childCounts")
  }

  override def postStop(): Unit = {
    tcpManager ! Unbind
  }

}

package com.dominikgruber.scalatorrent.peerwireprotocol.network

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp._
import akka.io.{IO, Tcp}
import com.dominikgruber.scalatorrent.peerwireprotocol.network.ConnectionManager.Config
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.util.{Asking, ExtraPattern}

import scala.concurrent.duration.FiniteDuration

class ConnectionManager(config: Config)
  extends Actor with ActorLogging with Asking {

  val coordinator: ActorRef = context.parent
  val tcpManager: ActorRef = IO(Tcp)(context.system)

  override def preStart(): Unit = {
    val endpoint = new InetSocketAddress("localhost", config.portIn)
    tcpManager ! Tcp.Bind(self, endpoint)
  }

  override def receive = {
    case ConnectionManager.CreateConnection(remoteAddress) => // from Coordinator.PeerConnRequestActor
      createConnRequestTempActor(remoteAddress, sender)

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
    override val timeoutDuration: FiniteDuration = config.tcpTimeout
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

  override def postStop(): Unit = {
    tcpManager ! Unbind
  }

}

object ConnectionManager {

  case class Config(portIn: Int, tcpTimeout: FiniteDuration)
  def props(config: Config) = Props(classOf[ConnectionManager], config)

  case class CreateConnection(remoteAddress: InetSocketAddress)
  case class Connected(peerConn: ActorRef, address: PeerAddress)
  case class Failed(message: Option[Throwable])
}

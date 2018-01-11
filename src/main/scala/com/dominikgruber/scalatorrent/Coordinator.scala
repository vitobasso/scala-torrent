package com.dominikgruber.scalatorrent

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.Coordinator._
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.HandshakeActor.TorrentInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.network.ConnectionManager.CreateConnection
import com.dominikgruber.scalatorrent.peerwireprotocol.network.PeerConnection.SetListener
import com.dominikgruber.scalatorrent.peerwireprotocol.network._
import com.dominikgruber.scalatorrent.peerwireprotocol.{HandshakeActor, InboundHandshake, OutboundHandshake}
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.util.{Asking, ExtraPattern}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class Coordinator(cli: ActorRef, config: Config) extends Actor with ActorLogging with Asking {

  val connManager: ActorRef = createConnManagerActor
  val torrents = mutable.Map.empty[String,(ActorRef, MetaInfo)]

  override def receive: Receive = {

    case AddTorrentFile(file) => // from Boot
      addTorrentFile(file)

    case ConnectToPeer(peer, meta) => // from Torrent
      log.debug(s"Requesting connection to $peer")
      createConnRequestTempActor(peer, meta, sender)

    case ConnectionManager.Connected(peerConn, address) => // inbound, from ConnectionManager
      val handshakeActor = createInboundHandshakeActor(peerConn, address)
      peerConn ! SetListener(handshakeActor)

    case IdentifyTorrent(infoHash) => // from PeerConnection
      torrents.get(infoHash) match {
        case Some((torrent, meta)) =>
          sender ! TorrentInfo(meta, torrent)
        case None => //TODO handle not found
      }
  }

  def addTorrentFile(file: String): Unit = {
    try {
      val meta = MetaInfo(new File(file))
      val torrentActor: ActorRef = createTorrentActor(meta)
      torrents(meta.hash) = (torrentActor, meta)
      scheduleReport(torrentActor)
      sender ! TorrentAddedSuccessfully(file, torrentActor)
      cli ! meta.fileInfo
    } catch {
      case e: Exception =>
        log.error("Failed to add torrent", e)
        sender ! TorrentFileInvalid(file, s"${e.getClass.getName}: ${e.getMessage}")
    }
  }

  def scheduleReport(torrent: ActorRef): Unit = {
    import com.dominikgruber.scalatorrent.cli.CliActor.ReportPlease

    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.duration._
    context.system.scheduler.schedule(0.millis, config.progressRefreshRate, torrent, ReportPlease(cli))
  }

  def createTorrentActor(meta: MetaInfo) = {
    val props = Torrent.props(meta, self, config.torrent)
    context.actorOf(props, "torrent-" + meta.hash)
  }

  def createOutboundHandshakeActor(peerConn: ActorRef, address: PeerAddress, meta: MetaInfo, torrent: ActorRef): ActorRef = {

    val props = OutboundHandshake.props(peerConn, address, config.handshake, meta, torrent)
    context.actorOf(props, s"handshake-out-$address-${meta.hash}")
  }

  def createInboundHandshakeActor(peerConn: ActorRef, address: PeerAddress): ActorRef = {
    val props = InboundHandshake.props(peerConn, address, config.handshake)
    context.actorOf(props, s"handshake-in-$address")
  }

  def createConnManagerActor: ActorRef = {
    val props = ConnectionManager.props(config.connMan)
    context.actorOf(props, "connection-manager")
  }

  def createConnRequestTempActor(peer: PeerAddress, meta: MetaInfo, torrent: ActorRef): ActorRef = {
    val props = Props(new PeerConnRequestActor(peer, meta, torrent))
    val name = s"temp-peer-connection-request-$peer-${meta.hash}"
    context.child(name) match { //TODO also check existing (established) PeerConnection?
      case Some(existing) => existing
      case None => context.actorOf(props, name)
    }
  }

  class PeerConnRequestActor(peer: PeerAddress, meta: MetaInfo, torrent: ActorRef)
    extends Actor with ActorLogging with ExtraPattern {
    override val timeoutDuration: FiniteDuration = config.connMan.tcpTimeout
    connManager ! CreateConnection(peer)
    override def receive: Receive = {
      case ConnectionManager.Connected(peerConn, address) => // outbound, from ConnectionManager
        log.debug(s"Peer connected: $address")
        val handshakeActor = createOutboundHandshakeActor(peerConn, address, meta, torrent)
        peerConn ! SetListener(handshakeActor)
        done()
      case ConnectionManager.Failed(cause) =>
        log.debug(s"Peer connection failed: $peer")
        torrent ! Coordinator.ConnectionFailed(peer, cause.map(_.getMessage))
        done()
    }

    override def onTimeout(): Unit = {
      log.warning(s"Peer connection request timed out: $peer")
      torrent ! Coordinator.ConnectionFailed(peer, Some(s"Timeout ($timeoutDuration)"))
    }

  }

}

object Coordinator {

  case class Config(progressRefreshRate: FiniteDuration, connMan: ConnectionManager.Config, handshake: HandshakeActor.Config, torrent: Torrent.Config)
  def props(cli: ActorRef, config: Config) = Props(classOf[Coordinator], cli, config)

  case class AddTorrentFile(file: String)
  case class TorrentAddedSuccessfully(file: String, torrent: ActorRef)
  case class TorrentFileInvalid(file: String, message: String)
  case class ConnectToPeer(address: PeerAddress, meta: MetaInfo)
  case class ConnectionFailed(peer: PeerAddress, cause: Option[String])
  case class IdentifyTorrent(infoHash: String)
}

package com.dominikgruber.scalatorrent.peerwireprotocol

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.Coordinator.IdentifyTorrent
import com.dominikgruber.scalatorrent.Torrent.PeerReady
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.HandshakeActor.{Config, TorrentInfo}
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.peerwireprotocol.message.Handshake
import com.dominikgruber.scalatorrent.tracker.{Peer, PeerAddress}
import com.dominikgruber.scalatorrent.util.Asking

trait HandshakeActor extends Actor with ActorLogging with Asking {
  selfType: {
    val peerConn: ActorRef
    val address: PeerAddress
    val config: Config
  } =>

  def sendHandshake(meta: MetaInfo): Unit = {
    val handshake = Handshake(config.pstr, config.extension, config.peerId, meta.fileInfo.infoHash)
    peerConn ! SendToPeer(handshake)
  }

  def finish(torrent: ActorRef, peerId: String) = {
    val peer = Peer(Some(peerId), address.ip, address.port)
    torrent ! PeerReady(peerConn, peer)
    context.stop(self)
  }
}
object HandshakeActor {
  case class Config(pstr: String, extension: Vector[Byte], peerId: String)

  case class TorrentInfo(metaInfo: MetaInfo, torrent: ActorRef)
}

class OutboundHandshake(val peerConn: ActorRef, val address: PeerAddress, val config: Config,
                        metaInfo: MetaInfo, torrent: ActorRef)
  extends HandshakeActor {

  override def preStart(): Unit = {
    sendHandshake(metaInfo)
  }

  override def receive: Receive = {
    case h: Handshake => // from PeerConnection
      //TODO validate handshake
      finish(torrent, h.peerId)
  }

}
object OutboundHandshake {
  def props(peerConn: ActorRef, address: PeerAddress, config: Config, metaInfo: MetaInfo, torrent: ActorRef) =
  Props(classOf[OutboundHandshake], peerConn, address, config, metaInfo, torrent)
}

class InboundHandshake(val peerConn: ActorRef, val address: PeerAddress, val config: Config)
  extends HandshakeActor {

  val coordinator: ActorRef = context.parent

  override def receive: Receive = {
    case h: Handshake => // from PeerConnection
      val response = coordinator ? IdentifyTorrent(h.infoHashString)
      response.onSuccess{
        case TorrentInfo(metaInfo, torrent) => // from Coordinator
          sendHandshake(metaInfo)
          finish(torrent, h.peerId)
      }
  }

}
object InboundHandshake {
  def props(peerConn: ActorRef, address: PeerAddress, config: Config) =
    Props(classOf[InboundHandshake], peerConn, address, config)
}

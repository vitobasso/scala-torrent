package com.dominikgruber.scalatorrent.peerwireprotocol

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.dominikgruber.scalatorrent.Coordinator.IdentifyTorrent
import com.dominikgruber.scalatorrent.SelfInfo.{extension, pstr, selfPeerId}
import com.dominikgruber.scalatorrent.Torrent.PeerReady
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.HandshakeActor.TorrentInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.peerwireprotocol.message.Handshake
import com.dominikgruber.scalatorrent.tracker.{Peer, PeerAddress}
import com.dominikgruber.scalatorrent.util.Asking

object HandshakeActor {
  case class TorrentInfo(metaInfo: MetaInfo, torrent: ActorRef)
}

trait HandshakeActor extends Actor with ActorLogging with Asking {
  selfType: {
    val peerConn: ActorRef
    val address: PeerAddress
  } =>

  def sendHandshake(meta: MetaInfo): Unit = {
    val handshake = Handshake(pstr, extension, selfPeerId, meta.fileInfo.infoHash)
    peerConn ! SendToPeer(handshake)
  }

  def finish(torrent: ActorRef, peerId: String) = {
    val peer = Peer(Some(peerId), address.ip, address.port)
    torrent ! PeerReady(peerConn, peer)
    context.stop(self)
  }
}

class OutboundHandshake(val peerConn: ActorRef, val address: PeerAddress, metaInfo: MetaInfo, torrent: ActorRef)
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

class InboundHandshake(val peerConn: ActorRef, val address: PeerAddress)
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


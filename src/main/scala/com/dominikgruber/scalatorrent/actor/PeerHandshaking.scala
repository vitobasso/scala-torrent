package com.dominikgruber.scalatorrent.actor

import akka.actor.ActorRef
import akka.io.Tcp._
import com.dominikgruber.scalatorrent.actor.Coordinator.{IdentifyTorrent, TorrentInfo}
import com.dominikgruber.scalatorrent.actor.PeerHandshaking._
import com.dominikgruber.scalatorrent.actor.ToByteString._
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.Handshake

object PeerHandshaking {
  val pstr = "BitTorrent protocol"
  val extension = Vector[Byte](0, 0, 0, 0, 0, 0, 0, 0)

  case class BeginConnection(torrent: ActorRef, metaInfo: MetaInfo)
  case class ReceiveConnection(tcp: ActorRef)
}

trait PeerHandshaking {
  peerConnection: PeerActor =>

  def handeshaking: Receive = {
    case BeginConnection(torrent, metaInfo) => // from Coordinator
      openTcp ! Connect(remoteAddress)
      context become Begin(metaInfo, torrent).apply
    case ReceiveConnection(tcp) => // from Coordinator
      tcp ! Register(self)
      context become Respond.apply
  }

  case class Begin(metaInfo: MetaInfo, torrent: ActorRef) {
    def apply: Receive = waitTcpConnection

    private def waitTcpConnection: Receive = {
      case Connected(_, _) => // from Tcp
        sender ! Register(self)
        sendHandshake(sender, metaInfo)
        context become waitSecondHandshake

      case CommandFailed(_: Connect) => // from Tcp
        log.error("Failed to open TCP connection")
        //TODO: Handle failure
    }

    private def waitSecondHandshake: Receive = {
      case Received(data) => // from Tcp
        Handshake.unmarshall(data.toVector) match {
          case Some(h: Handshake) =>
            log.debug(s"Received 2nd handshake: $h")
            //TODO validate handshake
            context become sharing(sender, metaInfo, torrent)
          case None =>
            log.warning(s"Failed to parse 2nd handshake: ${Hex(data)}")
            //TODO drop peer
        }
      case PeerClosed => handlePeerClosed // from Tcp
    }

  }

  case object Respond {
    def apply: Receive = waitFirstHandshake

    private def waitFirstHandshake: Receive = {
      case Received(data) => // from Tcp
        Handshake.unmarshall(data.toVector) match {
          case Some(h: Handshake) =>
            log.debug(s"Received 1st handshake: $h")
            coordinator ! IdentifyTorrent(h.infoHashString)
            context become waitTorrentInfo(sender, h)
          case None =>
            log.warning(s"Failed to parse 1st handshake: ${Hex(data)}")
            //TODO drop peer
        }
      case PeerClosed => handlePeerClosed // from Tcp
    }

    private def waitTorrentInfo(tcp: ActorRef, h: Handshake): Receive = {
      case TorrentInfo(metaInfo, torrent) => // from Coordinator
        sendHandshake(tcp, metaInfo)
        context become sharing(tcp, metaInfo, torrent)
    }
  }

  private def sendHandshake(tcp: ActorRef, metaInfo: MetaInfo) = {
    val handshake = Handshake(pstr, extension, selfPeerId, metaInfo.fileInfo.infoHash)
    tcp ! Write(handshake)
  }

}


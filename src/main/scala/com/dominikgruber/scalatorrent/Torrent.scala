package com.dominikgruber.scalatorrent

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.Coordinator.ConnectToPeer
import com.dominikgruber.scalatorrent.SelfInfo._
import com.dominikgruber.scalatorrent.Storage._
import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.cli.ProgressReporting.ReportPlease
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.{NothingToRequest, SendToPeer}
import com.dominikgruber.scalatorrent.peerwireprotocol.message.{Interested, Piece}
import com.dominikgruber.scalatorrent.peerwireprotocol.network.PeerConnection.SetListener
import com.dominikgruber.scalatorrent.peerwireprotocol.{PeerSharing, TransferState}
import com.dominikgruber.scalatorrent.tracker.Peer
import com.dominikgruber.scalatorrent.tracker.http.HttpTracker._
import com.dominikgruber.scalatorrent.tracker.http._
import com.dominikgruber.scalatorrent.tracker.udp.UdpTracker

import scala.collection.BitSet

object Torrent {
  /**
    * 16kB is standard. Sending a request with more might result in the peer dropping the connection
    * https://wiki.theory.org/index.php/BitTorrentSpecification#Info_in_Single_File_Mode
    */
  val BlockSize: Int = 16 * 1024
  case class PeerReady(conn: ActorRef, peer: Peer)
  case class AreWeInterested(partsAvailable: BitSet)
  case class NextRequest(partsAvailable: BitSet)
  case class ReceivedPiece(piece: Piece, partsAvailable: BitSet)
}

class Torrent(name: String, meta: MetaInfo, coordinator: ActorRef, portIn: Int)
  extends Actor with ActorLogging {

  val trackers: Seq[ActorRef] = trackerAddrs.flatMap { createTrackerActor }
  val storage: ActorRef = createStorageActor()
  val transferState = TransferState(meta)

  override def preStart(): Unit = {
    storage ! StatusPlease
  }

  override def receive: Receive = catchingUp

  def catchingUp: Receive = {
    case Status(piecesWeHave) =>
      log.info(s"Resuming with ${piecesWeHave.size} pieces out of ${meta.fileInfo.numPieces}")
      piecesWeHave foreach transferState.markPieceCompleted
      trackers.foreach { _ ! SendEventStarted(0, 0) }
      context become findingPeers
    case Complete =>
      //TODO seed
  }

  def findingPeers: Receive = {
    case s: TrackerResponseWithSuccess => // from Tracker
      log.debug(s"[$name] Request to Tracker successful: $s")
      connectToPeers(s.peers)
      context become sharing

    case f: TrackerResponseWithFailure =>
      log.warning(s"[$name] Request to Tracker failed: ${f.reason}")

    case TrackerConnectionFailed(msg) =>
      log.warning(s"[$name] Connection to Tracker failed: $msg")
  }

  def sharing: Receive = {

    case PeerReady(peerConn, peer) => // from HandshakeActor
      val peerSharing = createPeerSharingActor(peerConn, peer)
      peerConn ! SetListener(peerSharing)

    case AreWeInterested(piecesAvailable) => // from PeerSharing
      if(transferState.isAnyPieceNew(piecesAvailable))
        sender ! SendToPeer(Interested())

    case NextRequest(piecesAvailable) => // from PeerSharing
      requestNewBlocks(piecesAvailable, sender) //TODO begin with 5 requests

    case ReceivedPiece(piece, piecesAvailable) => // from PeerSharing
      //TODO validate numbers received
      transferState
        .addBlock(piece.index, piece.begin/BlockSize, piece.block.toArray)
        .foreach { completePiece =>
            storage ! Store(piece.index, completePiece)
        }
      requestNewBlocks(piecesAvailable, sender)

    case ReportPlease =>
      sender ! transferState.report
  }

  def requestNewBlocks(piecesAvailable: BitSet, peerSharing: ActorRef): Unit = {
    transferState.forEachNewRequest(piecesAvailable) {
      request => peerSharing ! SendToPeer(request)
    } elseIfEmpty {
      peerSharing ! NothingToRequest
    }
  }

  def connectToPeers(peers: List[Peer]): Unit = {
    val unique = peers.groupBy(_.address).map{
      case (_, duplicates) => duplicates.head
    }
    unique.foreach{
      peer => coordinator ! ConnectToPeer(peer, meta)
    }
  }

  private def createPeerSharingActor(peerConn: ActorRef, peer: Peer) = {
    val props = Props(classOf[PeerSharing], peerConn, peer, meta)
    context.actorOf(props, s"peer-sharing-${peer.address}")
  }

  private def createTrackerActor(peerUrl: String): Option[ActorRef] = {
    val url = """(\w+)://(.*):(\d+)""".r
    val props = peerUrl match {
      case url("http", _, _) =>
        Some(Props(classOf[HttpTracker], meta, selfPeerId, portIn)) //TODO rm selfPeerId param
      case url("udp", host, port) =>
        Some(Props(classOf[UdpTracker], meta.fileInfo, new InetSocketAddress(host, port.toInt)))
      case _ => None
    }
    val escapedUrl = peerUrl.replaceAll("/", "_")
    props.map{
      context.actorOf(_, s"tracker-$escapedUrl-${meta.hash}")
    }
  }

  private def createStorageActor(): ActorRef = {
    val props = Props(classOf[Storage], meta.fileInfo)
    context.actorOf(props, s"storage-${meta.hash}")
  }

  private def trackerAddrs: Seq[String] = meta.announceList.map { _.flatten }.getOrElse(Seq(meta.announce))

}

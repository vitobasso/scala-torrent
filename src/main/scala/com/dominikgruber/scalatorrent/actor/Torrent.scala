package com.dominikgruber.scalatorrent.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.actor.Coordinator.ConnectToPeer
import com.dominikgruber.scalatorrent.actor.PeerConnection.SetListener
import com.dominikgruber.scalatorrent.actor.PeerSharing.{NothingToRequest, SendToPeer}
import com.dominikgruber.scalatorrent.actor.Storage.{Status, StatusPlease, Store}
import com.dominikgruber.scalatorrent.actor.Torrent._
import com.dominikgruber.scalatorrent.actor.Tracker.{SendEventStarted, TrackerConnectionFailed}
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.metainfo.SelfInfo._
import com.dominikgruber.scalatorrent.peerwireprotocol.{Interested, Piece}
import com.dominikgruber.scalatorrent.terminal.ProgressReporting.ReportPlease
import com.dominikgruber.scalatorrent.tracker.{Peer, TrackerResponseWithFailure, TrackerResponseWithSuccess}
import com.dominikgruber.scalatorrent.transfer.TransferStatus

import scala.collection.BitSet

object Torrent {
  /**
    * 16kB is standard. Sending a request with more might result in the peer dropping the connection
    * https://wiki.theory.org/index.php/BitTorrentSpecification#Info_in_Single_File_Mode
    */
  val BlockSize: Int = 16 * 1024
  val MaxActivePieces: Int = 1
  case class PeerReady(conn: ActorRef, peer: Peer)
  case class AreWeInterested(partsAvailable: BitSet)
  case class NextRequest(partsAvailable: BitSet)
  case class ReceivedPiece(piece: Piece, partsAvailable: BitSet)
}

class Torrent(name: String, meta: MetaInfo, coordinator: ActorRef, portIn: Int)
  extends Actor with ActorLogging {

  val tracker: ActorRef = createTrackerActor()
  val storage: ActorRef = createStorageActor()

  override def preStart(): Unit = {
    storage ! StatusPlease
  }

  override def receive: Receive = catchingUp

  def catchingUp: Receive = {
    case Status(piecesWeHave) =>
      piecesWeHave foreach transferStatus.completePiece
      tracker ! SendEventStarted(0, 0)
      context become findingPeers
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

  val transferStatus = TransferStatus(meta)

  def sharing: Receive = {

    case PeerReady(peerConn, peer) => // from HandshakeActor
      val peerSharing = createPeerSharingActor(peerConn, peer)
      peerConn ! SetListener(peerSharing)

    case AreWeInterested(piecesAvailable) => // from PeerSharing
      if(transferStatus.isAnyPieceNew(piecesAvailable))
        sender ! SendToPeer(Interested())

    case NextRequest(piecesAvailable) => // from PeerSharing
      requestNewBlock(piecesAvailable, sender) //TODO begin with 5 requests

    case ReceivedPiece(piece, piecesAvailable) => // from PeerSharing
      //TODO validate numbers received
      transferStatus
        .addBlock(piece.index, piece.begin/BlockSize, piece.block)
        .foreach { completePiece =>
            storage ! Store(piece.index, completePiece)
        }
      requestNewBlock(piecesAvailable, sender)

    case ReportPlease =>
      sender ! transferStatus.report
  }

  def requestNewBlock(piecesAvailable: BitSet, peerSharing: ActorRef): Unit =
    transferStatus.pickNewBlock(piecesAvailable) match {
      case Some(request) =>
        peerSharing ! SendToPeer(request)
      case None =>
        peerSharing ! NothingToRequest
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

  private def createTrackerActor(): ActorRef = {
    val props = Props(classOf[Tracker], meta, selfPeerId, portIn)
    context.actorOf(props, s"tracker-${meta.hash}")
  }

  private def createStorageActor(): ActorRef = {
    val props = Props(classOf[Storage], meta.fileInfo)
    context.actorOf(props, s"storage-${meta.hash}")
  }

}

package com.dominikgruber.scalatorrent

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.Coordinator.ConnectToPeer
import com.dominikgruber.scalatorrent.PeerFinder.{FindPeers, PeersFound}
import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.cli.ProgressReporting.ReportPlease
import com.dominikgruber.scalatorrent.metainfo.{MetaInfo, PieceChecksum}
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.{NothingToRequest, SendToPeer}
import com.dominikgruber.scalatorrent.peerwireprotocol.message.{Interested, Piece}
import com.dominikgruber.scalatorrent.peerwireprotocol.network.PeerConnection.SetListener
import com.dominikgruber.scalatorrent.peerwireprotocol.{PeerSharing, TransferState}
import com.dominikgruber.scalatorrent.storage.Storage
import com.dominikgruber.scalatorrent.storage.Storage._
import com.dominikgruber.scalatorrent.tracker.{Peer, PeerAddress}

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

class Torrent(meta: MetaInfo, coordinator: ActorRef, peerPortIn: Int, nodePortIn: Int)
  extends Actor with ActorLogging {

  //lazy prevents unwanted init before overwrite from test
  lazy val peerFinder: ActorRef = createPeerFinderActor()
  lazy val storage: ActorRef = createStorageActor()
  val transferState = TransferState(meta)
  val checksum = PieceChecksum(meta)

  override def preStart(): Unit = {
    storage ! StatusPlease
  }

  override def receive: Receive = catchingUp

  def catchingUp: Receive = {
    case Status(piecesWeHave) =>
      log.info(s"Resuming with ${piecesWeHave.size} pieces out of ${meta.fileInfo.numPieces}")
      piecesWeHave foreach transferState.markPieceCompleted
      peerFinder ! FindPeers
      context become sharing
    case Complete =>
      //TODO seed
  }

  def sharing: Receive = {

    case PeersFound(peers) => connectToPeers(peers)

    case PeerReady(peerConn, peer) => // from HandshakeActor
      val peerSharing = createPeerSharingActor(peerConn, peer)
      peerConn ! SetListener(peerSharing)

    case AreWeInterested(piecesAvailable) => // from PeerSharing
      if(transferState.isAnyPieceNewIn(piecesAvailable))
        sender ! SendToPeer(Interested())

    case NextRequest(piecesAvailable) => // from PeerSharing
      requestNewBlocks(piecesAvailable, sender)

    case ReceivedPiece(piece, piecesAvailable) => // from PeerSharing
      //TODO validate numbers received
      transferState
        .addBlock(piece.index, piece.begin/BlockSize, piece.block.toArray)
        .foreach { completePiece =>
          if(checksum(piece.index, completePiece))
            storage ! Store(piece.index, completePiece)
          else {
            log.warning(s"Checksum failed for piece ${piece.index}.")
            transferState.resetPiece(piece.index)
          }
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

  def connectToPeers(peers: Set[PeerAddress]): Unit =
    peers.foreach{
      peer => coordinator ! ConnectToPeer(peer, meta)
    }

  private def createPeerFinderActor() = {
    val props = Props(classOf[PeerFinder], meta, peerPortIn, nodePortIn, self)
    context.actorOf(props, s"peer-finder")
  }

  private def createPeerSharingActor(peerConn: ActorRef, peer: Peer) = {
    val props = Props(classOf[PeerSharing], peerConn, peer, meta)
    context.actorOf(props, s"peer-sharing-${peer.address}")
  }

  private def createStorageActor(): ActorRef = {
    val props = Props(classOf[Storage], meta.fileInfo)
    context.actorOf(props, s"storage")
  }

}

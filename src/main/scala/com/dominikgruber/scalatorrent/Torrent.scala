package com.dominikgruber.scalatorrent

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.Coordinator.ConnectToPeer
import com.dominikgruber.scalatorrent.PeerFinder.{FindPeers, PeersFound}
import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.cli.CliActor.ReportPlease
import com.dominikgruber.scalatorrent.metainfo.{MetaInfo, PieceChecksum}
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.{NothingToRequest, SendToPeer}
import com.dominikgruber.scalatorrent.peerwireprotocol.message.{Interested, Piece, Request}
import com.dominikgruber.scalatorrent.peerwireprotocol.network.PeerConnection.SetListener
import com.dominikgruber.scalatorrent.peerwireprotocol.{PeerSharing, TransferState}
import com.dominikgruber.scalatorrent.storage.Storage
import com.dominikgruber.scalatorrent.storage.Storage._
import com.dominikgruber.scalatorrent.tracker.{Peer, PeerAddress}
import com.dominikgruber.scalatorrent.util.ByteUtil.Bytes

import scala.collection.BitSet

class Torrent(meta: MetaInfo, coordinator: ActorRef, config: Config)
  extends Actor with ActorLogging {

  //lazy prevents unwanted init before overwrite from test
  lazy val peerFinder: ActorRef = createPeerFinderActor()
  lazy val storage: ActorRef = createStorageActor()
  val transferState = TransferState(meta, config.transferState)
  val checksum = PieceChecksum(meta)

  override def preStart(): Unit = {
    storage ! StatusPlease
  }

  override def receive: Receive = initializing

  def initializing: Receive = {
    case Status(piecesWeHave) =>
      log.info(s"Resuming with ${piecesWeHave.size} pieces out of ${meta.fileInfo.numPieces}")
      piecesWeHave foreach transferState.markPieceCompleted
      peerFinder ! FindPeers
      context become ready
    case Complete =>
      //TODO seed
  }

  def ready: Receive =
    managingPeers orElse
    sharing orElse
    reportingProgress

  def managingPeers: Receive = {
    case PeersFound(peers) =>
      log.debug(s"< Peers found: ${peers.size}")
      peers.foreach {
        peer => coordinator ! ConnectToPeer(peer, meta)
      }
    case PeerReady(peerConn, peer) => // from HandshakeActor
      log.info(s"< Peer ready: ${peer.address}")
      val peerSharing = createPeerSharingActor(peerConn, peer)
      peerConn ! SetListener(peerSharing)
      peerFinder ! PeerConnected(peer.address)
    case PeerSharing.Closed(peerAddress) =>
      log.info(s"< Peer closed: $peerAddress")
      peerFinder ! PeerClosed(peerAddress, None)
    case Coordinator.ConnectionFailed(peerAddress, cause) =>
      log.info(s"< Peer connection failed: $peerAddress")
      peerFinder ! PeerClosed(peerAddress, cause)
  }

  def sharing: Receive = {
    case AreWeInterested(piecesAvailable) => // from PeerSharing
      if(transferState.isAnyPieceNewIn(piecesAvailable))
        sender ! SendToPeer(Interested())

    case NextRequest(peer, piecesAvailable) => // from PeerSharing
      log.debug(s"< NextRequest")
      requestNewBlocks(peer, piecesAvailable, sender)

    case ReceivedPiece(piece, peer, piecesAvailable) => // from PeerSharing
      //TODO validate numbers received
      transferState
        .addBlock(piece.index, piece.begin/BlockSize, piece.block.toArray)
        .foreach { checkAndStore(piece.index, _) } //if piece is now complete
      requestNewBlocks(peer, piecesAvailable, sender)
  }

  def reportingProgress: Receive = {
    case ReportPlease(listener) => // from Coordinator
      listener ! transferState.report
      peerFinder ! ReportPlease(listener)
  }

  def checkAndStore(index: Int, bytes: Bytes): Unit =
    if(checksum(index, bytes))
      storage ! Store(index, bytes)
    else {
      log.warning(s"Checksum failed for piece $index.")
      transferState.resetPiece(index)
    }

  def requestNewBlocks(peer: PeerAddress, piecesAvailable: BitSet, peerSharing: ActorRef): Unit =
    transferState.produceNewRequests(peer, piecesAvailable) match {
      case None =>
        log.debug(s"> NothingToRequest")
        peerSharing ! NothingToRequest
      case Some(Nil) =>
        log.debug(s"Out of budget")
      case Some(requests) if requests.nonEmpty =>
        requests.foreach { request: Request =>
          peerSharing ! SendToPeer(request)
        }
    }

  private def createPeerFinderActor() = {
    val props = PeerFinder.props(meta, self, config.peerFinder)
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

object Torrent {

  case class Config(transferState: TransferState.Config, peerFinder: PeerFinder.Config)
  def props(meta: MetaInfo, coordinator: ActorRef, config: Config) =
    Props(classOf[Torrent], meta, coordinator, config)

  /**
    * 16kB is standard. Sending a request with more might result in the peer dropping the connection
    * https://wiki.theory.org/index.php/BitTorrentSpecification#Info_in_Single_File_Mode
    */
  val BlockSize: Int = 16 * 1024
  case class PeerReady(conn: ActorRef, peer: Peer)
  case class PeerConnected(peer: PeerAddress)
  case class PeerClosed(peer: PeerAddress, cause: Option[String])
  case class AreWeInterested(partsAvailable: BitSet)
  case class NextRequest(peer: PeerAddress, partsAvailable: BitSet)
  case class ReceivedPiece(piece: Piece, peer: PeerAddress, partsAvailable: BitSet)
}

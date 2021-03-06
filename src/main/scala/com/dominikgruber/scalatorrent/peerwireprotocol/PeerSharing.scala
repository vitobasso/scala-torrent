package com.dominikgruber.scalatorrent.peerwireprotocol

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing._
import com.dominikgruber.scalatorrent.peerwireprotocol.message._
import com.dominikgruber.scalatorrent.peerwireprotocol.network.PeerConnection
import com.dominikgruber.scalatorrent.tracker.{Peer, PeerAddress}
import com.dominikgruber.scalatorrent.util.Asking

import scala.collection.BitSet

class PeerSharing(peerConn: ActorRef, peer: Peer, metaInfo: MetaInfo)
  extends Actor with ActorLogging with Asking {

  val torrent: ActorRef = context.parent

  /**
    * Whether or not the remote peer has choked this client. When a peer chokes
    * the client, it is a notification that no requests will be answered until
    * the client is unchoked. The client should not attempt to send requests for
    * blocks, and it should consider all pending (unanswered) requests to be
    * discarded by the remote peer.
    */
  var peerChoking = true
  var amChoking = true

  /**
    * Whether or not the remote peer is interested in something this client has
    * to offer. This is a notification that the remote peer will begin requesting
    * blocks when the client unchokes them.
    */
  var peerInterested = false
  var amInterested = false

  var bitfield: Vector[Boolean] = Vector.fill(metaInfo.fileInfo.numPieces)(false)

  override def receive: Receive = {
    case p: Piece => // from PeerConnection
      torrent ! ReceivedPiece(p, peer.address, toBitSet(bitfield))
    case b: Bitfield => // from PeerConnection
      //TODO validate length
      bitfield = b.availablePieces
      checkIfInterested()
    case Have(index) => // from PeerConnection
      bitfield = bitfield.updated(index, true)
      if(!amInterested) checkIfInterested()
    case _: Unchoke => // from PeerConnection
      peerChoking = false
      log.debug(s"> NextRequest")
      torrent ! NextRequest(peer.address, toBitSet(bitfield))
    case NothingToRequest => // from Torrent
      log.debug(s"< NothingToRequest")
      amInterested = false
      peerConn ! SendToPeer(NotInterested())
    case msgToSend: SendToPeer => // from Torrent
      peerConn ! msgToSend
    case PeerConnection.Closed =>
      log.debug(s"< Peer closed")
      torrent ! PeerSharing.Closed(peer.address)
  }

  def checkIfInterested(): Unit = {
    val response = torrent ? AreWeInterested(toBitSet(bitfield))
    response.onSuccess {
      case msgToSend @ SendToPeer(msg) =>
        amInterested = msg.isInstanceOf[Interested]
        peerConn ! msgToSend
    }
  }

  def toBitSet(bools: Seq[Boolean]): BitSet = {
    val indexesOfTrue = bools.zipWithIndex.collect { case (true, i) => i }
    BitSet(indexesOfTrue: _*)
  }

}

object PeerSharing {
  def props(peerConn: ActorRef, peer: Peer, meta: MetaInfo) = Props(classOf[PeerSharing], peerConn, peer, meta)

  case class SendToPeer(msg: MessageOrHandshake)
  case object NothingToRequest
  case class Closed(peer: PeerAddress)
}

package com.dominikgruber.scalatorrent.actor

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.dominikgruber.scalatorrent.actor.PeerSharing.{NothingToRequest, SendToPeer}
import com.dominikgruber.scalatorrent.actor.Torrent.{AreWeInterested, NextRequest}
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol._
import com.dominikgruber.scalatorrent.tracker.Peer
import com.dominikgruber.scalatorrent.transfer.BitSetUtil
import com.dominikgruber.scalatorrent.util.Asking

object PeerSharing {
  case class SendToPeer(msg: MessageOrHandshake)
  case object NothingToRequest
}

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
      torrent ! p
    case b: Bitfield => // from PeerConnection
      //TODO validate length
      bitfield = b.availablePieces
      checkIfInterested()
    case Have(index) => // from PeerConnection
      bitfield = bitfield.updated(index, true)
      if(!amInterested) checkIfInterested()
    case _: Unchoke => // from PeerConnection
      peerChoking = false
      torrent ! NextRequest(BitSetUtil.fromBooleans(bitfield))
    case NothingToRequest => // from Torrent
      amInterested = false
      peerConn ! SendToPeer(NotInterested())
    case msgToSend: SendToPeer => // from Torrent
      peerConn ! msgToSend
    case _ => //TODO
  }

  def checkIfInterested(): Unit = {
    val response = torrent ? AreWeInterested(BitSetUtil.fromBooleans(bitfield))
    response.onSuccess {
      case msgToSend @ SendToPeer(msg) =>
        amInterested = msg.isInstanceOf[Interested]
        peerConn ! msgToSend
    }
  }


}

package com.dominikgruber.scalatorrent

import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.peerwireprotocol.message._
import com.dominikgruber.scalatorrent.storage.Storage.Status
import com.dominikgruber.scalatorrent.util.Mocks

import scala.collection.BitSet

class TorrentLeechingSpec extends TorrentSpec {

  override val meta: MetaInfo = Mocks.metaInfo(
    totalLength = 8 * BlockSize,
    pieceLength = 2 * BlockSize)
  val allAvailable = BitSet(0, 1, 2, 3)
  val totalBlocks: Long = meta.fileInfo.totalBytes/BlockSize
  val peer = Mocks.peer.address

  "a Torrent actor, when downloading" must {

    torrent ! Status(BitSet.empty) //trigger initialization & become "sharing"

    "send Interested when a peer has new pieces" in {
      torrent ! AreWeInterested(BitSet(0))
      val SendToPeer(msg) = expectMsgType[SendToPeer]
      msg shouldBe Interested()
    }

    "send 5 Requests in response to MoreRequests" in {
      torrent ! NextRequest(peer, allAvailable)
      for(_ <- 1 to 5) //5 = TransferState.SimultaneousRequests
        ObservedRequests.expectRequest()
      expectNoMsg()
    }

    "request 2 blocks from the same piece" in {
      ObservedRequests.received match {
        case Seq(req1, req2, _, _, _) =>
          req2.index shouldBe req1.index      //same piece
          req2.begin should not be req1.begin //diff block
      }
    }

    "request 2 more blocks from a 2nd piece" in {
      ObservedRequests.received match {
        case Seq(req1, _, req3, req4, _) =>
          req3.index should not be req1.index //new piece
          req4.index shouldBe req3.index      //but same piece
          req4.begin should not be req3.begin //diff block
      }
    }

    "request 1 more block from a 3rd piece" in {
      ObservedRequests.received match {
        case Seq(req1, _, req3, _, req5) =>
          req5.index should not be req1.index //new piece
          req5.index should not be req3.index
      }
    }

    "send a new Request in response to ReceivedPiece" in {
      val firstRequest = ObservedRequests.received.head
      val piece = Piece(firstRequest.index, firstRequest.begin, Vector.empty)
      torrent ! ReceivedPiece(piece, peer, allAvailable)
      ObservedRequests.expectRequest()
      expectNoMsg()
    }

    "not send Interested when a peer hasn't got any new pieces" in {
      val secondRequest = ObservedRequests.received(1)
      val piece = Piece(secondRequest.index, secondRequest.begin, Vector.empty)
      torrent ! ReceivedPiece(piece, peer, allAvailable)
      ObservedRequests.expectRequest()
      expectNoMsg()

      val pieceWeAlreadyHave = secondRequest.index
      torrent ! AreWeInterested(BitSet(pieceWeAlreadyHave))
      expectNoMsg()
    }

    object ObservedRequests {
      var received = Seq.empty[Request]

      def expectRequest(): Unit = {
        val SendToPeer(msg) = expectMsgType[SendToPeer]
        msg shouldBe a[Request]
        ObservedRequests.received = ObservedRequests.received :+ msg.asInstanceOf[Request]
      }
    }

  }

}
package com.dominikgruber.scalatorrent

import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.cli.FrontendActor.ReportPlease
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.{NothingToRequest, SendToPeer}
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState.ProgressReport
import com.dominikgruber.scalatorrent.peerwireprotocol.message.Piece
import com.dominikgruber.scalatorrent.storage.Storage._
import com.dominikgruber.scalatorrent.util.Mocks

import scala.collection.BitSet

class TorrentRestoringProgressSpec extends TorrentSpec {

  override val meta: MetaInfo = Mocks.metaInfo(
    totalLength = 6 * BlockSize,
    pieceLength = 2 * BlockSize)
  val allAvailable = BitSet(0, 1, 2)
  val peer = Mocks.peer.address

  "a Torrent actor, when restoring progress from a file" must {

    "report some progress" in {
      torrent //trigger actor creation (this is a lazy val)
      storage.expectMsg(StatusPlease)
      torrent ! Status(BitSet(1, 2))

      torrent ! ReportPlease
      expectMsg(ProgressReport(2/3.0, Seq(0, 1, 1)))
    }

    "store a complete Piece" in {
      val index = 0

      val bytes0 = Mocks.block(0.toByte)
      val block0 = Piece(index, 0 * BlockSize, bytes0)
      torrent ! ReceivedPiece(block0, peer, allAvailable)
      storage.expectNoMsg()
      expectMsgType[SendToPeer]

      val bytes1 = Mocks.block(1.toByte)
      val block1 = Piece(index, 1 * BlockSize, bytes1)
      torrent ! ReceivedPiece(block1, peer, allAvailable)
      storage.expectMsgPF() {
        case Store(`index`, data) =>
          data should contain theSameElementsInOrderAs (bytes0 ++ bytes1)
      }
      expectMsg(NothingToRequest)
    }

    "report further progress" in {
      torrent ! ReportPlease
      expectMsg(ProgressReport(1, Seq(1, 1, 1)))
    }

  }

}
package com.dominikgruber.scalatorrent

import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.cli.CliActor.ReportPlease
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState._
import com.dominikgruber.scalatorrent.peerwireprotocol.message.Piece
import com.dominikgruber.scalatorrent.storage.Storage._
import com.dominikgruber.scalatorrent.util.Mocks

import scala.collection.BitSet

class TorrentStoringNewSpec extends TorrentSpec {

  override val meta: MetaInfo = Mocks.metaInfo(
    totalLength = 8 * BlockSize,
    pieceLength = 2 * BlockSize)
  val allAvailable = BitSet(0, 1, 2, 3)
  val peer = Mocks.peer.address

  "a Torrent actor, when starting a torrent from scratch" must {

    "begin with no progress" in {
      torrent //trigger actor creation (this is a lazy val)
      storage.expectMsg(StatusPlease)
      torrent ! Status(BitSet.empty)

      torrent ! ReportPlease(testActor)
      expectMsg(ProgressReport(0, Seq(0, 0, 0, 0)))
    }

    "store a complete Piece" in {
      val index = 1

      val bytes0 = Mocks.block(0.toByte)
      val block0 = Piece(index, 0 * BlockSize, bytes0)
      torrent ! ReceivedPiece(block0, peer, allAvailable)
      storage.expectNoMsg()
      for(_ <- 1 to config.transferState.tcpPipelining)
        expectMsgType[SendToPeer]

      val bytes1 = Mocks.block(1.toByte)
      val block1 = Piece(index, 1 * BlockSize, bytes1)
      torrent ! ReceivedPiece(block1, peer, allAvailable)
      storage.expectMsgPF() {
        case Store(`index`, data) =>
          data should contain theSameElementsInOrderAs (bytes0 ++ bytes1)
      }
      expectMsgType[SendToPeer]
    }

    "report further progress" in {
      torrent ! ReportPlease(testActor)
      expectMsg(ProgressReport(1/4.0, Seq(0, 1, 0, 0)))
    }

  }

}
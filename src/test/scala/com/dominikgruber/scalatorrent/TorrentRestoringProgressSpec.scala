package com.dominikgruber.scalatorrent

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.Coordinator.ConnectToPeer
import com.dominikgruber.scalatorrent.Storage._
import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.cli.ProgressReporting.ReportPlease
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.{NothingToRequest, SendToPeer}
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState.ProgressReport
import com.dominikgruber.scalatorrent.peerwireprotocol.message.Piece
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

import scala.collection.BitSet

class TorrentRestoringProgressSpec extends ActorSpec {
  outer =>

  val meta: MetaInfo = Mocks.metaInfo(
    totalLength = 6 * BlockSize,
    pieceLength = 2 * BlockSize)
  val tracker = TestProbe("tracker")
  val storage = TestProbe("storage")
  val coordinator = TestProbe("coordinator")
  lazy val allAvailable = BitSet(0, 1, 2)

  val torrent: ActorRef = {
    def createActor = new Torrent("", meta, coordinator.ref, 0) {
      override val trackers: Seq[ActorRef] = Seq(outer.tracker.ref)
      override val storage: ActorRef = outer.storage.ref
    }
    system.actorOf(Props(createActor), "torrent")
  }

  "a Torrent actor, when restoring progress from a file" must {

    "report some progress" in {
      //after creation
      storage.expectMsg(StatusPlease)
      torrent ! Status(BitSet(1, 2))

      torrent ! Mocks.trackerResponse()
      coordinator expectMsg ConnectToPeer(Mocks.peer, meta)

      torrent ! ReportPlease
      expectMsg(ProgressReport(2/3.0, Seq(0, 1, 1)))
    }

    "store a complete Piece" in {
      val index = 0

      val bytes0 = Mocks.block(0.toByte)
      val block0 = Piece(index, 0 * BlockSize, bytes0)
      torrent ! ReceivedPiece(block0, allAvailable)
      storage.expectNoMsg()
      expectMsgType[SendToPeer]

      val bytes1 = Mocks.block(1.toByte)
      val block1 = Piece(index, 1 * BlockSize, bytes1)
      torrent ! ReceivedPiece(block1, allAvailable)
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
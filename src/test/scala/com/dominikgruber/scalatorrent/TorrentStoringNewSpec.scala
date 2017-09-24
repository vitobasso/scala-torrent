package com.dominikgruber.scalatorrent

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.Coordinator.ConnectToPeer
import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.cli.ProgressReporting.ReportPlease
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState._
import com.dominikgruber.scalatorrent.peerwireprotocol.message.Piece
import com.dominikgruber.scalatorrent.storage.Storage._
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

import scala.collection.BitSet

class TorrentStoringNewSpec extends ActorSpec {
  outer =>

  val meta: MetaInfo = Mocks.metaInfo(
    totalLength = 8 * BlockSize,
    pieceLength = 2 * BlockSize)
  val tracker = TestProbe("tracker")
  val storage = TestProbe("storage")
  val coordinator = TestProbe("coordinator")
  lazy val allAvailable = BitSet(0, 1, 2, 3)

  val torrent: ActorRef = {
    def createActor = new Torrent("", meta, coordinator.ref, 0) {
      override lazy val trackers: Seq[ActorRef] = Seq(outer.tracker.ref)
      override lazy val storage: ActorRef = outer.storage.ref
    }
    system.actorOf(Props(createActor), "torrent")
  }

  "a Torrent actor, when starting a torrent from scratch" must {

    "begin with no progress" in {
      //after creation
      storage.expectMsg(StatusPlease)
      torrent ! Status(BitSet.empty)

      torrent ! Mocks.trackerResponse()
      coordinator expectMsg ConnectToPeer(Mocks.peer, meta)

      torrent ! ReportPlease
      expectMsg(ProgressReport(0, Seq(0, 0, 0, 0)))
    }

    "store a complete Piece" in {
      val index = 1

      val bytes0 = Mocks.block(0.toByte)
      val block0 = Piece(index, 0 * BlockSize, bytes0)
      torrent ! ReceivedPiece(block0, allAvailable)
      storage.expectNoMsg()
      for(_ <- 1 to SimultaneousRequests)
        expectMsgType[SendToPeer]

      val bytes1 = Mocks.block(1.toByte)
      val block1 = Piece(index, 1 * BlockSize, bytes1)
      torrent ! ReceivedPiece(block1, allAvailable)
      storage.expectMsgPF() {
        case Store(`index`, data) =>
          data should contain theSameElementsInOrderAs (bytes0 ++ bytes1)
      }
      expectMsgType[SendToPeer]
    }

    "report further progress" in {
      torrent ! ReportPlease
      expectMsg(ProgressReport(1/4.0, Seq(0, 1, 0, 0)))
    }

  }

}
package com.dominikgruber.scalatorrent.actor

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.actor.Coordinator.ConnectToPeer
import com.dominikgruber.scalatorrent.actor.Storage.{Status, StatusPlease, Store}
import com.dominikgruber.scalatorrent.actor.Torrent.{BlockSize, ReceivedPiece}
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.Piece
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

import scala.collection.BitSet

class TorrentStoringSpec extends ActorSpec {
  outer =>

  val meta: MetaInfo = Mocks.metaInfo(
    totalLength = 6 * BlockSize,
    pieceLength = 2 * BlockSize)
  val tracker = TestProbe("tracker")
  val storage = TestProbe("storage")
  val coordinator = TestProbe("coordinator")
  lazy val allAvailable = BitSet(0, 1, 2)

  "a Torrent actor" must {

    val torrent: ActorRef = {
      def createActor = new Torrent("", meta, coordinator.ref, 0) {
        override val tracker: ActorRef = outer.tracker.ref
        override val storage: ActorRef = outer.storage.ref
      }
      system.actorOf(Props(createActor), "torrent")
    }

    "check with pieces we have in the file" in {
      //after creation
      storage.expectMsg(StatusPlease)
    }

    "become sharing" in {
      torrent ! Status(BitSet.empty)
      torrent ! Mocks.trackerResponse()
      coordinator expectMsg ConnectToPeer(Mocks.peer, meta)
    }

    "store a Piece when completed" in {
      val index = 1

      val bytes0 = Mocks.block(0.toByte)
      val block0 = Piece(index, 0 * BlockSize, bytes0)
      torrent ! ReceivedPiece(block0, allAvailable)
      storage.expectNoMsg()

      val bytes1 = Mocks.block(1.toByte)
      val block1 = Piece(index, 1 * BlockSize, bytes1)
      torrent ! ReceivedPiece(block1, allAvailable)
      storage.expectMsg(Store(index, bytes0 ++ bytes1))
    }

  }

}
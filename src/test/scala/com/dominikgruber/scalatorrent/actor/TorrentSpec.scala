package com.dominikgruber.scalatorrent.actor

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.actor.Coordinator.ConnectToPeer
import com.dominikgruber.scalatorrent.actor.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.actor.Torrent.{AreWeInterested, BlockSize, NextRequest, ReceivedPiece}
import com.dominikgruber.scalatorrent.actor.Tracker.{SendEventStarted, TrackerResponseReceived}
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.{Interested, Piece, Request}
import com.dominikgruber.scalatorrent.tracker.{Peer, TrackerResponseWithSuccess}
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

import scala.collection.BitSet

class TorrentSpec extends ActorSpec {
  outer =>

  val meta: MetaInfo = Mocks.metaInfo(
    totalLength = 6 * BlockSize,
    pieceLength = 2 * BlockSize)
  val tracker = TestProbe("tracker")
  val coordinator = TestProbe("coordinator")
  val totalBlocks = meta.fileInfo.totalBytes/BlockSize

  "test pre-conditions" must {
    "be satisfied" in {
      // following tests depend on this
      totalBlocks shouldBe 6
      meta.fileInfo.numPieces shouldBe 3
    }
  }

  "a Torrent actor" must {

    val torrent: ActorRef = {
      def createActor = new Torrent("", meta, coordinator.ref, 0) {
        override val tracker: ActorRef = outer.tracker.ref
      }
      system.actorOf(Props(createActor), "torrent")
    }

    "say hi to tracker" in {
      //after creating the actor
      tracker expectMsg SendEventStarted(0, 0)
    }

    "create peer connections" in {
      val peer1 = Peer(None, "ip1", 0)
      val peer2 = Peer(None, "ip2", 0)
      torrent ! TrackerResponseReceived {
        TrackerResponseWithSuccess(0, None, None, 0, 0, List(peer1, peer2), None)
      }
      coordinator expectMsg ConnectToPeer(peer1, meta)
      coordinator expectMsg ConnectToPeer(peer2, meta)
    }

    "send Interested when a peer has new pieces" in {
      torrent ! AreWeInterested(BitSet(0))
      val SendToPeer(msg) = expectMsgType[SendToPeer]
      msg shouldBe Interested()
    }

    "send Request in response to MoreRequests" in {
      torrent ! NextRequest(allAvailable)
      ObservedRequests.expectRequest //TODO x5
    }

    "send Request in response to ReceivedPiece" in {
      val firstRequest = {
        ObservedRequests.received.size shouldBe 1
        ObservedRequests.received.head
      }
      val piece = Piece(firstRequest.index, firstRequest.begin, Vector.empty)
      torrent ! ReceivedPiece(piece, allAvailable)
      ObservedRequests.expectRequest
    }

    "pick the same index for the first and second Requests" in {
      val (firstRequest, secondRequest) = {
        ObservedRequests.received.size shouldBe 2
        (ObservedRequests.received.head, ObservedRequests.received(1))
      }
      secondRequest.index shouldBe firstRequest.index
      secondRequest.begin should not be firstRequest.begin
    }

    "not send Interested when a peer hasn't got any new pieces" in {
      val secondRequest = {
        ObservedRequests.received.size shouldBe 2
        ObservedRequests.received(1)
      }
      val piece = Piece(secondRequest.index, secondRequest.begin, Vector.empty)
      torrent ! ReceivedPiece(piece, allAvailable)
      ObservedRequests.expectRequest

      val pieceWeAlreadyHave = secondRequest.index
      torrent ! AreWeInterested(BitSet(pieceWeAlreadyHave))
      expectNoMsg()
    }

    object ObservedRequests {
      var received = Seq.empty[Request]

      def expectRequest: Unit = {
        val SendToPeer(msg) = expectMsgType[SendToPeer]
        msg shouldBe a[Request]
        ObservedRequests.received = ObservedRequests.received :+ msg.asInstanceOf[Request]
      }
    }

    lazy val allAvailable = BitSet(0, 1, 2)
  }

}
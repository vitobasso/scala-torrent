package com.dominikgruber.scalatorrent.actor

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.actor.PeerSharing.{NothingToRequest, SendToPeer}
import com.dominikgruber.scalatorrent.actor.Torrent.{AreWeInterested, NextRequest}
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.{NotInterested, _}
import com.dominikgruber.scalatorrent.tracker.Peer
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

class PeerSharingSpec extends ActorSpec {
  outer =>

  val address = new InetSocketAddress("dummy", 123)
  val meta: MetaInfo = Mocks.metaInfo()
  val peerId = "peer-id-has-20-chars"
  val peer = Peer(Some(peerId), "dummy", 123)
  val torrent = TestProbe("torrent")
  val peerConn = TestProbe("peer-connection")


  "a PeerSharing actor" must {
    val peerSharing = {
      def createActor = new PeerSharing(peerConn.ref, peer, meta) {
        override val torrent: ActorRef = outer.torrent.ref
      }
      system.actorOf(Props(createActor), "peer-sharing")
    }

    "ask AreWeInterested after receiving Bitfield" in {
      peerSharing ! Bitfield(Vector(true, false))
      torrent.expectMsgType[AreWeInterested]
      torrent reply SendToPeer(Interested())
      peerConn expectMsg SendToPeer(Interested())
    }

    "stay quiet after receiving Have if already interested" in {
      peerSharing ! Have(0)
      torrent.expectNoMsg
    }

    "ask NextRequest after receiving Unchoke" in {
      peerSharing ! Unchoke()
      torrent.expectMsgType[NextRequest]
    }

    "deliver Pieces to the torrent actor" in {
      peerSharing ! Piece(0, 0, Vector(0, 0))
      torrent.expectMsgType[Piece]
    }

    "forward messages to the peer" in {
      peerSharing ! SendToPeer(Interested())
      peerConn expectMsg SendToPeer(Interested())
    }

    "send NotInterested if there's NothingToRequest" in {
      peerSharing ! NothingToRequest
      peerConn expectMsg SendToPeer(NotInterested())
    }

    "ask AreWeInterested after receiving Have if not interested" in {
      peerSharing ! Have(0)
      torrent.expectMsgType[AreWeInterested]
      torrent reply SendToPeer(NotInterested())
      peerConn expectMsg SendToPeer(NotInterested())
    }

    "ask AreWeInterested after receiving Have if still not interested" in {
      peerSharing ! Have(0)
      torrent.expectMsgType[AreWeInterested]
      torrent reply SendToPeer(Interested())
      peerConn expectMsg SendToPeer(Interested())
    }

    "stop asking AreWeInterested after receiving Have if now interested" in {
      peerSharing ! Have(0)
      torrent.expectNoMsg
    }

  }

}

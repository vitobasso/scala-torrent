package com.dominikgruber.scalatorrent.actor

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.actor.Coordinator.IdentifyTorrent
import com.dominikgruber.scalatorrent.actor.HandshakeActor.TorrentInfo
import com.dominikgruber.scalatorrent.actor.PeerSharing.SendToPeer
import com.dominikgruber.scalatorrent.actor.Torrent.PeerReady
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.metainfo.SelfInfo._
import com.dominikgruber.scalatorrent.peerwireprotocol._
import com.dominikgruber.scalatorrent.tracker.Peer
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

class HandshakeActorSpec extends ActorSpec {
  outer =>

  val address = new InetSocketAddress("dummy", 123)
  val meta: MetaInfo = Mocks.metaInfo()
  val theirPeerId = "their-peer-id-has-20"
  val theirHandshake = Handshake(pstr, extension, theirPeerId, Mocks.infoHash)
  val ourHandshake = Handshake(`pstr`, `extension`, `selfPeerId`, meta.fileInfo.infoHash)
  val coordinator = TestProbe("coordinator")
  val torrent = TestProbe("torrent")
  val peerConn = TestProbe("peer-connection")

  "an OutboundHandshake actor" must {
    val outboundHandshake = {
      def createActor = new OutboundHandshake(peerConn.ref, address, meta, torrent.ref)
      system.actorOf(Props(createActor), "outbound-handshake")
    }
    watch(outboundHandshake)

    "send the 1st Handshake" in
      shouldSendHandshake

    "notify Torrent after receiving the 2nd Handshake" in {
      outboundHandshake ! theirHandshake
      shouldSendPeerReady
    }

    "stop itself" in
      expectTerminated(outboundHandshake)

  }

  "an InboundHandshake actor" must {
    val inboundHandshake = {
      def createActor = new InboundHandshake(peerConn.ref, address){
        override val coordinator: ActorRef = outer.coordinator.ref
      }
      system.actorOf(Props(createActor), "inbound-handshake")
    }
    watch(inboundHandshake)

    "receive the 1st handshake then identify" in {
      inboundHandshake ! theirHandshake
      coordinator.expectMsgType[IdentifyTorrent]
    }

    "send the 2nd handshake after receiving identification" in {
      coordinator reply TorrentInfo(meta, torrent.ref)
      shouldSendHandshake
    }

    "notify Torrent after the 2nd handshake" in
      shouldSendPeerReady

    "stop itself" in
      expectTerminated(inboundHandshake)

  }

  private def shouldSendHandshake = {
    peerConn.expectMsgPF() {
      case SendToPeer(msg) =>
        msg shouldBe ourHandshake
    }
  }

  private def shouldSendPeerReady = {
    torrent.expectMsgPF() {
      case PeerReady(actor, Peer(Some(peerId), host, port)) =>
        actor shouldBe peerConn.ref
        peerId shouldBe theirPeerId
        host shouldBe "dummy"
        port shouldBe 123
    }
  }

}

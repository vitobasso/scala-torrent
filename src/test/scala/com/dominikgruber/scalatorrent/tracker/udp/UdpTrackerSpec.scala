package com.dominikgruber.scalatorrent.tracker.udp

import java.net.InetSocketAddress

import akka.actor.{ActorRef, Props}
import akka.io.UdpConnected
import akka.testkit.TestProbe
import akka.util.ByteString
import com.dominikgruber.scalatorrent.tracker.Peer
import com.dominikgruber.scalatorrent.tracker.http.HttpTracker.SendEventStarted
import com.dominikgruber.scalatorrent.tracker.http.TrackerResponseWithSuccess
import com.dominikgruber.scalatorrent.util.ByteUtil.bytes
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

class UdpTrackerSpec extends ActorSpec {
  outer =>

  val udpManager = TestProbe("udp-manager")
  val udpConn = TestProbe("udp-connection")
  val meta = Mocks.fileMetaInfo()
  val peerAddr: InetSocketAddress = Mocks.peer.address
  val tracker: ActorRef = {
    def createActor = new UdpTracker(meta, peerAddr) {
      override val udpManager: ActorRef = outer.udpManager.ref
    }
    system.actorOf(Props(createActor), "tracker")
  }

  "a UDP Tracker actor" must {

    "connect upon creation" in {
      udpManager expectMsg UdpConnected.Connect(tracker, peerAddr)
      udpConn.send(tracker, UdpConnected.Connected)
    }

    //TODO check that msgs are stashed until ready

    "send a Connect request" in {
      tracker ! SendEventStarted(1, 2)
      udpConn.expectMsgPF() {
        case UdpConnected.Send(msg, _) =>
          msg.size shouldBe 16 // Connect request
      }
    }

    "send an Announce request" in {
      val connectResponse = bytes {
        "00 00 00 00 " + //action
        "00 00 00 01 " + //transaction id
        "00 00 00 00 00 00 00 02" //connection id
      }

      tracker ! UdpConnected.Received(ByteString(connectResponse))
      udpConn.expectMsgPF() {
        case UdpConnected.Send(msg, _) =>
          msg.size shouldBe 98 // Announce request
      }
    }

    "return peers" in {
      val announceResponse = bytes {
        "00 00 00 01 " + //action
        "00 00 00 01 " + //transaction id
        "00 00 00 02 " + //interval
        "00 00 00 03 " + //leechers
        "00 00 00 04 " + //seeders
        "00 00 00 05 " + //peer ip
        "00 06" //peer port
      }

      tracker ! UdpConnected.Received(ByteString(announceResponse))
      expectMsgPF() {
        case TrackerResponseWithSuccess(
        interval, _, _, complete, incomplete, peers, _) =>
          interval shouldBe 2
          complete shouldBe 4
          incomplete shouldBe 3
          peers shouldBe List(Peer(None, "0.0.0.5", 6))

      }
    }

  }

}

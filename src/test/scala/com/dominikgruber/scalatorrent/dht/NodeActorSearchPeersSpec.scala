package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress

import com.dominikgruber.scalatorrent.dht.NodeActor.{FoundPeers, SearchPeers}
import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}
import com.dominikgruber.scalatorrent.dht.Util._
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import org.scalatest.concurrent.Eventually._

class NodeActorSearchPeersSpec extends NodeActorSpec {

  "a NodeActor, when searching peers" must {

    "query the closest nodes found in the routing table (first step)" in test { fixture =>
      import fixture._
      stubRoutingTable(hash("01"))
        .returns(Seq(nodeInfo("08"), nodeInfo("09")))
      nodeActor ! SearchPeers(hash("01"))

      udp.expectMsgPF() {
        case SendToNode(GetPeers(_, origin, target), remote) =>
          origin shouldBe selfNode
          target shouldBe hash("01")
          PeerInfo.parse(remote) shouldBe Right(PeerInfo(Ip(8), Port(8)))
      }

      udp.expectMsgPF() {
        case SendToNode(GetPeers(_, _, _), remote) =>
          PeerInfo.parse(remote) shouldBe Right(PeerInfo(Ip(9), Port(9)))
      }
    }

    "query the nodes received (following steps)" in test { fixture =>
      import fixture._
      val transaction = startSearch(hash("01"), nodeInfo("08"))(fixture)

      nodeActor ! peersNotFound(transaction, node("08"), nodeInfo("04"))

      udp.expectMsgPF() {
        case SendToNode(GetPeers(_, origin, target), remote) =>
          origin shouldBe selfNode
          target shouldBe hash("01")
          PeerInfo.parse(remote) shouldBe Right(PeerInfo(Ip(4), Port(4)))
      }
    }

    "return peers found" in test { fixture =>
      import fixture._
      val transaction = startSearch(hash("01"), nodeInfo("08"))(fixture)

      nodeActor ! peersFound(transaction, node("08"), peerInfo("0A"))

      expectMsg(FoundPeers(hash("01"), Seq(peerInfo("0A"))))
    }

    "remember peers found" in test { fixture =>
      import fixture._
      val transaction = startSearch(hash("01"), nodeInfo("08"))(fixture)

      nodeActor ! peersFound(transaction, node("08"), peerInfo("0A"))

      eventually {
        (peerMap.add _).verify(where { (torrent, peers) =>
          torrent == hash("01") && peers == Set(peerInfo("0A"))
        })
      }
    }

    "not query nodes if not getting closer to target" in test { fixture =>
      import fixture._
      val transaction = startSearch(hash("01"), nodeInfo("08"))(fixture)

      assume(node("0C").distance(node("01")) > node("08").distance(node("01")))
      nodeActor ! peersNotFound(transaction, node("08"), nodeInfo("0C")) //0C is farther to 1 than 8 in xor distance

      udp.expectNoMsg
    }

    "not continue a search from an unknown transaction" in test { fixture =>
      import fixture._
      val knownTrans = startSearch(hash("01"), nodeInfo("08"))(fixture)

      val unknownTrans = TransactionId("0")
      assume(knownTrans != unknownTrans)
      nodeActor ! peersNotFound(unknownTrans, node("08"), nodeInfo("04"))

      udp.expectNoMsg
    }

    "not continue a search from an unknown origin" in test { fixture =>
      import fixture._
      val transaction = startSearch(hash("01"), nodeInfo("08"))(fixture)

      nodeActor ! peersNotFound(transaction, node("02"), nodeInfo("04"))

      udp.expectNoMsg
    }

  }

  def startSearch(target: InfoHash, firstNode: NodeInfo)(fixture: NodeActorFixture): TransactionId = {
    import fixture._
    stubRoutingTable(target).returns(Seq(firstNode))
    nodeActor ! SearchPeers(target)
    udp.expectMsgPF() {case SendToNode(GetPeers(trans, _, _), _) => trans}
  }

  def peersNotFound(trans: TransactionId, origin: NodeId, nodes: NodeInfo): ReceivedFromNode = {
    val notFound = PeersNotFound(trans, origin, Token(""), Seq(nodes))
    ReceivedFromNode(notFound, mock[InetSocketAddress])
  }

  def peersFound(trans: TransactionId, origin: NodeId, peers: PeerInfo): ReceivedFromNode = {
    val found = PeersFound(trans, origin, Token(""), Seq(peers))
    ReceivedFromNode(found, mock[InetSocketAddress])
  }

}

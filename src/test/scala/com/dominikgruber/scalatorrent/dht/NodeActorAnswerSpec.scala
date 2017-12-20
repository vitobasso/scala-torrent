package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress

import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}
import com.dominikgruber.scalatorrent.dht.Util._
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._

class NodeActorAnswerSpec extends NodeActorSpec {

  val remoteAddr: InetSocketAddress = mock[InetSocketAddress]
  val transaction = TransactionId("123")
  val originId: NodeId = node("01")

  implicit class FixtureOps(fixture: NodeActorFixture) {
    def received(msg: (TransactionId, NodeId) => Query): Unit =
      fixture.nodeActor ! ReceivedFromNode(msg(transaction, originId), remoteAddr)

    def expectSend(msg: (TransactionId, NodeId) => Response): Unit =
      fixture.udp expectMsg SendToNode(msg(transaction, fixture.selfNode), remoteAddr)
  }

  "when receiving Ping" must {

    "answer with Pong" in test { fixture =>
      fixture.received(Ping)
      fixture.expectSend(Pong)
    }

  }

  "when receiving FindNode" must {
    val targetNode: NodeId = node("02")

    "answer with some nodes" in test { fixture =>
      val closerNode = nodeInfo("03")
      assume(closerNode.id.distance(targetNode) < fixture.selfNode.distance(targetNode))
      fixture.stubRoutingTable(targetNode).returns(Seq(closerNode))
      fixture.received(FindNode(_, _, targetNode))

      fixture.expectSend(NodesFound(_, _, Seq(closerNode)))
    }

    "not include nodes that are farther from target than 'self'" in test { fixture =>
      val closeNode = nodeInfo("03")
      assume(closeNode.id.distance(targetNode) < fixture.selfNode.distance(targetNode))
      val farNode = nodeInfo("0A")
      assume(farNode.id.distance(targetNode) > fixture.selfNode.distance(targetNode))
      fixture.stubRoutingTable(targetNode).returns(Seq(farNode, closeNode))
      fixture.received(FindNode(_, _, targetNode))

      fixture.expectSend(NodesFound(_, _, Seq(closeNode)))
    }

    "not answer if can't find any node" in test { fixture => //TODO or should answer with empty?
      fixture.stubRoutingTable(targetNode).returns(Seq.empty)
      fixture.received(FindNode(_, _, targetNode))

      fixture.udp expectNoMsg()
    }

    "not answer if can't find any nodes closer than 'self'" in test { fixture =>  //TODO or should answer with empty?
      val farNode = nodeInfo("0A")
      assume(farNode.id.distance(targetNode) > fixture.selfNode.distance(targetNode))
      fixture.stubRoutingTable(targetNode).returns(Seq(farNode))
      fixture.received(FindNode(_, _, targetNode))

      fixture.udp expectNoMsg()
    }

  }

  "when receiving GetPeers" must {
    val targetHash: InfoHash = hash("02")

    "answer with some peers" in test { fixture =>
      val knownPeer = peerInfo("03")
      fixture.stubPeerMap(targetHash).returns(Set(knownPeer))
      fixture.received(GetPeers(_, _, targetHash))

      fixture.udp.expectMsgPF(){
        case SendToNode(PeersFound(`transaction`, fixture.selfNode, _, peers, _), `remoteAddr`) =>
          peers shouldBe Seq(knownPeer)
      }
    }

    "answer with nodes if can't find peer" in test { fixture =>
      val closerNode = nodeInfo("03")
      assume(closerNode.id.distance(targetHash) < fixture.selfNode.distance(targetHash))
      fixture.stubPeerMap(targetHash).returns(Set.empty)
      fixture.stubRoutingTable(targetHash).returns(Seq(closerNode))
      fixture.received(GetPeers(_, _, targetHash))

      fixture.udp.expectMsgPF(){
        case SendToNode(PeersNotFound(`transaction`, fixture.selfNode, _, nodes), `remoteAddr`) =>
          nodes shouldBe Seq(closerNode)
      }
    }

    "not include nodes that are farther from target than 'self'" in test { fixture =>
      val closeNode = nodeInfo("03")
      assume(closeNode.id.distance(targetHash) < fixture.selfNode.distance(targetHash))
      val farNode = nodeInfo("0A")
      assume(farNode.id.distance(targetHash) > fixture.selfNode.distance(targetHash))
      fixture.stubPeerMap(targetHash).returns(Set.empty)
      fixture.stubRoutingTable(targetHash).returns(Seq(closeNode, farNode))
      fixture.received(GetPeers(_, _, targetHash))

      fixture.udp.expectMsgPF(){
        case SendToNode(PeersNotFound(`transaction`, fixture.selfNode, _, nodes), `remoteAddr`) =>
          nodes shouldBe Seq(closeNode)
      }
    }

    "not answer if can't find peers or nodes closer than 'self'" in test { fixture => //TODO or should answer with empty?
      fixture.stubPeerMap(targetHash).returns(Set.empty)
      fixture.stubRoutingTable(targetHash).returns(Seq.empty)
      fixture.received(GetPeers(_, _, targetHash))

      fixture.udp expectNoMsg()
    }

  }

}

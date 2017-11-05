package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress

import com.dominikgruber.scalatorrent.dht.NodeActor.SearchNode
import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}
import com.dominikgruber.scalatorrent.dht.Util._
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import org.scalatest.concurrent.Eventually._

class NodeActorSearchNodesSpec extends NodeActorSpec {

  "a NodeActor, when searching nodes" must {

    "query the closest nodes found in the routing table (first step)" in test { fixture =>
      import fixture._

      stubRoutingTable(node("01")).returns(Seq(nodeInfo("08"), nodeInfo("09")))
      nodeActor ! SearchNode(node("01"))

      udp.expectMsgPF() {
        case SendToNode(FindNode(_, origin, target), remote) =>
          origin shouldBe selfNode
          target shouldBe node("01")
          PeerInfo.parse(remote) shouldBe Right(PeerInfo(Ip(8), Port(8)))
      }

      udp.expectMsgPF() {
        case SendToNode(FindNode(_, _, _), remote) =>
          PeerInfo.parse(remote) shouldBe Right(PeerInfo(Ip(9), Port(9)))
      }
    }

    "query the nodes received (following steps)" in test { fixture =>
      import fixture._
      val transaction = startSearch(node("01"), nodeInfo("08"))(fixture)

      nodeActor ! nodesFound(transaction, nodeInfo("08"), nodeInfo("04"))

      udp.expectMsgPF() {
        case SendToNode(FindNode(_, origin, target), remote) =>
          origin shouldBe selfNode
          target shouldBe node("01")
          PeerInfo.parse(remote) shouldBe Right(PeerInfo(Ip(4), Port(4)))
      }
    }

    "add responsive nodes to table" in test { fixture =>
      import fixture._
      val transaction = startSearch(node("01"), nodeInfo("08"))(fixture)

      nodeActor ! nodesFound(transaction, nodeInfo("08"), nodeInfo("0A"))

      eventually {
        (routingTable.add _).verify(where { (info: NodeInfo) =>
          info == nodeInfo("08")
        })
      }
    }

  }

  def startSearch(target: NodeId, firstNode: NodeInfo)(fixture: NodeActorFixture): TransactionId = {
    import fixture._
    stubRoutingTable(target).returns(Seq(firstNode))
    nodeActor ! SearchNode(target)
    udp.expectMsgPF() {case SendToNode(FindNode(trans, _, _), _) => trans}
  }

  def nodesFound(trans: TransactionId, origin: NodeInfo, nodes: NodeInfo): ReceivedFromNode = {
    val notFound = NodesFound(trans, origin.id, Seq(nodes))
    val address = new InetSocketAddress(origin.ip.toString, origin.port.toInt)
    ReceivedFromNode(notFound, address)
  }

}

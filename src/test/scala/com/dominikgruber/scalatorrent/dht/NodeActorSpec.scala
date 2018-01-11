package com.dominikgruber.scalatorrent.dht

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.dht.Util.node
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import com.dominikgruber.scalatorrent.util.ActorSpec
import org.scalamock.handlers.CallHandler1

trait NodeActorSpec extends ActorSpec {

  def test(testBody: NodeActorFixture => Unit): Unit = {
    val fixture = new NodeActorFixture()
    try {
      testBody(fixture)
    } finally {
      syncStop(fixture.nodeActor)
    }
  }

  case class NodeActorFixture() {
    outer =>

    val selfNode: NodeId = node("00")
    val udp = TestProbe("udp")
    val routingTable: RoutingTable = stub[RoutingTable]
    val peerMap: PeerMap = stub[PeerMap]

    val nodeActor: ActorRef = {
      val config = NodeActor.Config(selfNode, 0)
      def createActor = new NodeActor(config) {
        override val routingTable: RoutingTable = outer.routingTable
        override val peerMap: PeerMap = outer.peerMap
        override lazy val udp: ActorRef = outer.udp.ref
      }
      system.actorOf(Props(createActor), "node")
    }

    def stubRoutingTable(arg: Id20B): CallHandler1[Id20B, Seq[NodeInfo]] =
      (routingTable.findClosestNodes _).when(arg)

    def stubPeerMap(arg: InfoHash): CallHandler1[InfoHash, Set[PeerInfo]] =
      (peerMap.get _).when(arg)

  }
}
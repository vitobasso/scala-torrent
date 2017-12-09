package com.dominikgruber.scalatorrent.dht

import java.nio.charset.StandardCharsets

import akka.actor.{ActorRef, Props}
import com.dominikgruber.scalatorrent.SelfInfo
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import com.dominikgruber.scalatorrent.util.{ActorIT, ByteUtil}
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

import com.dominikgruber.scalatorrent.dht.NodeActor.{FoundPeers, SearchPeers}


class NodeActorIT extends ActorIT with Eventually {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 1.second)

  "the NodeActor" should {

    val nodeActor: ActorRef = {
      def createActor = new SpiedNodeActor(SelfInfo.nodeId)
      syncStart(Props(createActor), "node")
    }

    "fill the routing table" in {
      table(nodeActor).nBucketsUsed shouldBe 1

      eventually {
        table(nodeActor).nBucketsUsed should be > 1
      }
    }

    "find peers for a torrent hash" in {
      val hash: InfoHash = infoHash("08 AD A5 A7 A6 18 3A AE 1E 09 D8 31 DF 67 48 D5 66 09 5A 10") //Sintel movie
      nodeActor ! SearchPeers(hash)

      eventually {
        val found = expectMsgType[FoundPeers]
        found.target shouldBe hash
        found.peers should not be empty
      }
    }

    //TODO receive queries w/ invalid remote address
  }

  def table(actor: ActorRef): RoutingTable = {
    actor ! ReturnRoutingTable
    expectMsgType[RoutingTable]
  }

  case object ReturnRoutingTable
  class SpiedNodeActor(selfId: NodeId) extends NodeActor(selfId, 50000) {
    override def receive: Receive = super.receive.orElse {
      case ReturnRoutingTable => sender ! routingTable
    }
  }

  def node(idArg: String, ipArg: String, portArg: Int): NodeInfo = {
    val idStr = new String(ByteUtil.bytes(idArg), StandardCharsets.ISO_8859_1)
    val res: Either[String, NodeInfo] =
      for {
        id <- NodeId.validate(idStr).right
        ip <- Ip.parse(ipArg).right
        port <- Port.parse(portArg).right
      } yield NodeInfo(id, Address(ip, port))
    res.right.get
  }

  def infoHash(hex: String): InfoHash =
    InfoHash.validateHex(hex).right.get

}

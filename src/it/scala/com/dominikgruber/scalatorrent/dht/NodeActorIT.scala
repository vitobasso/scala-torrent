package com.dominikgruber.scalatorrent.dht

import java.nio.charset.StandardCharsets

import akka.actor.{ActorRef, Props}
import com.dominikgruber.scalatorrent.SelfInfo
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import com.dominikgruber.scalatorrent.util.{ActorIT, ByteUtil}
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class NodeActorIT extends ActorIT with Eventually {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 1.second)

  "the NodeActor" should {

    val nodeActor: ActorRef = {
      def createActor = new SpiedNodeActor(SelfInfo.nodeId)
      syncStart(Props(createActor), "node")
    }

    "initialize the routing table" in {
      table(nodeActor).nBucketsUsed shouldBe 1

      eventually {
        table(nodeActor).nBucketsUsed should be > 1
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

}

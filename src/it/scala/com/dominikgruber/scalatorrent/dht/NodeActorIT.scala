package com.dominikgruber.scalatorrent.dht

import java.nio.charset.StandardCharsets

import akka.actor.{ActorRef, Props}
import com.dominikgruber.scalatorrent.SelfInfo
import com.dominikgruber.scalatorrent.dht.NodeActor.AddNode
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import com.dominikgruber.scalatorrent.util.{ActorIT, ByteUtil}
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class NodeActorIT extends ActorIT with Eventually {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds, interval = 1.second)

  val realPeers = Set(
//    node("12 ee f6 0c 2f 63 b7 da dd 6e 93 44 c3 0d 62 ce 54 a5 51 5e", "46.39.231.106", 10241),
//    node("78 34 14 6b f2 66 e4 08 2f b0 3e 97 f0 66 74 2d c6 62 48 96", "122.227.92.242", 1126)
    node("78 34 14 6b f2 66 e4 08 2f b0 3e 97 f0 66 74 2d c6 62 48 96", "62.138.0.158", 6969)
  )

  "the NodeActor" should {

    val nodeActor: ActorRef = {
      def createActor = new SpiedNodeActor(SelfInfo.nodeId)
      syncStart(Props(createActor), "node")
    }

    "start with an empty routing table" in {
      nodeActor ! ReturnRoutingTable
      val table = expectMsgType[RoutingTable]
      table.nBucketsUsed shouldBe 1
    }

    "initialize the routing table" in {
      realPeers.foreach { node =>
        nodeActor ! AddNode(node)
      }

      eventually {
        nodeActor ! ReturnRoutingTable
        val table = expectMsgType[RoutingTable]
        table.nBucketsUsed should be > 1
      }
    }

    //TODO receive queries w/ invalid remote address
  }

  case object ReturnRoutingTable
  class SpiedNodeActor(selfId: NodeId) extends NodeActor(selfId) {
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

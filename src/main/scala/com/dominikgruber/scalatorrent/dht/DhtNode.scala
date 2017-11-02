package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.dominikgruber.scalatorrent.SelfInfo
import com.dominikgruber.scalatorrent.dht.DhtMessage._
import com.dominikgruber.scalatorrent.dht.DhtNode._
import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}

/**
  * http://www.bittorrent.org/beps/bep_0005.html
  */
case object DhtNode {

  case class Transaction(node: NodeInfo, id: TransactionId)

  //find peers
  //  d = distance(infohash, node)  for node in local routing table
  //  select x closest nodes, ask each
  //  stop if
  //    found enough peers; or
  //    can't find closer node
  //  store (in local routing table?) contact of x responding nodes closest to infohash

}

case class DhtNode(selfNode: NodeId, udpSender: ActorRef) extends Actor with ActorLogging {

  val table = RoutingTable(SelfInfo.nodeId)

  var pendingRequests: Map[Transaction, Long] = Map.empty

  override def receive: Receive = {
    case ReceivedFromNode(msg, remote) => msg match {
      case q: Query => handleQuery(q, remote)
      case r: Response => handleResponse(r, remote)
    }
  }

  def handleQuery(msg: Query, remote: InetSocketAddress): Receive = {
    case Ping(trans, origin) => //TODO update node on table
      send(remote, Pong(trans, selfNode))
    case FindNode(trans, origin, target) =>
      val nodes: Seq[NodeInfo] = table.findClosestNode(target).toSeq //TODO 8 closest nodes
      send(remote, NodesFound(trans, selfNode, nodes))
    case GetPeers(trans, origin, hash) =>
      ???
  }

  def send(remote: InetSocketAddress, message: Message): Unit =
    udpSender ! SendToNode(message, remote)

  def handleResponse(msg: Response, remote: InetSocketAddress): Receive = {
    case Pong(trans, origin) =>
      ???
    case NodesFound(trans, origin, nodes) =>
      ???
    case PeersFound(trans, origin, token, peers) =>
      ???
    case PeersNotFound(trans, origin, token, nodes) =>
      ???
  }

}
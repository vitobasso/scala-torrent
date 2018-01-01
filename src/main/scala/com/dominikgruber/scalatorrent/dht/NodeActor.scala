package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import cats.syntax.either._
import com.dominikgruber.scalatorrent.dht.NodeActor._
import com.dominikgruber.scalatorrent.dht.SearchManager.NodeInfoOrAddress
import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * A node in a DHT network
  * http://www.bittorrent.org/beps/bep_0005.html
  */
case class NodeActor(selfNode: NodeId, port: Int) extends Actor with ActorLogging {

  val routingTable = RoutingTable(selfNode) //TODO persist
  val peerMap = PeerMap() //TODO persist
  lazy val udp: ActorRef = createUdpSocketActor //lazy prevents init before overwrite from test

  override def preStart(): Unit = {
    udp //trigger lazy init
    scheduleCleanup()
    considerDiscoveringNewNodes()
  }

  override def receive: Receive = {
    case SearchNode(id) =>
      Searches.start(id, sender)
    case SearchPeers(hash) =>
      Searches.start(hash, sender)
    case AddNode(info) =>
      routingTable.add(info)
    case ReceivedFromNode(msg, remote) => //from UdpSocket
      handleNodeMessage(msg, remote)
    case StopSearch(id: Id20B) =>
      Searches.stop(id, sender)
    case CleanInactiveSearches =>
      Searches.cleanInactive()
      considerDiscoveringNewNodes() //maybe a previous node search timed out, let's try again
  }

  private def handleNodeMessage(message: Message, remote: InetSocketAddress): Unit =
    message match {
      case q: Query =>
        handleQuery(q, remote)
        resolveOrigin(q.origin, remote) {
          routingTable.add
        }
      case r: Response =>
        resolveOrigin(r.origin, remote) { info =>
          handleResponse(r, info)
          routingTable.add(info)
        }
      case Error(_, code, message) =>
        log.error(s"Received dht error $code: $message")
    }

  private def handleQuery(msg: Query, remote: InetSocketAddress): Unit = msg match {
    case Ping(trans, origin) =>
      send(remote, Pong(trans, selfNode))
    case FindNode(trans, origin, target) =>
      answerFindNode(remote, trans, target)
    case GetPeers(trans, origin, hash) =>
      answerGetPeers(remote, trans, hash)
    case _ => log.warning(s"Can't handle query: $msg")
  }

  private def handleResponse(msg: Response, info: NodeInfo): Unit = msg match {
    case Pong(trans, origin) =>
      //noop: already updated table
    case NodesFound(trans, origin, nodes) =>
      Searches.continue(trans, info, nodes)
    case PeersFound(trans, origin, token, peers, nodes) => //TODO do something with nodes
      reportPeersFound(trans, info, peers)
    case PeersNotFound(trans, origin, token, nodes) =>
      Searches.continue(trans, info, nodes)
    case _ => log.warning(s"Can't handle response: $msg")
  }

  private def send(remote: InetSocketAddress, message: Message): Unit =
    udp ! SendToNode(message, remote)

  private def resolveOrigin(id: NodeId, addr: InetSocketAddress)(handle: NodeInfo => Unit): Unit =
    NodeInfo.parse(id, addr) match {
      case Right(origin) => handle(origin)
      case Left(err) => log.warning(s"Can't resolve origin address: $err")
    }

  private def reportPeersFound(trans: TransactionId, origin: NodeInfo, peers: Seq[PeerInfo]): Unit =
    Searches.remember(origin, trans) match {
      case Right(Search(target: InfoHash, requester)) =>
        peerMap.add(target, peers.toSet)
        requester ! NodeActor.FoundPeers(target, peers)
      case Right(search) => log.warning(s"Can't report peers found: This transaction was in a search for ${search.target}")
      case Left(err) => log.warning(s"Can't report peers found: $err")
    }

  private def answerFindNode(remote: InetSocketAddress, trans: TransactionId, target: NodeId): Unit = {
    val nodes = findCloserNodes(target)
    if (nodes.nonEmpty)
      send(remote, NodesFound(trans, selfNode, nodes))
  }

  private def answerGetPeers(remote: InetSocketAddress, trans: TransactionId, hash: InfoHash): Unit = {
    val token = Token.forIp(remote.getHostName)
    val peers = peerMap.get(hash)
    if(peers.nonEmpty)
      send(remote, PeersFound(trans, selfNode, Some(token), peers.toSeq, Seq.empty)) //TODO include nodes
    else {
      val nodes = findCloserNodes(hash)
      if (nodes.nonEmpty)
        send(remote, PeersNotFound(trans, selfNode, token, nodes))
    }
  }

  private def findCloserNodes(target: Id20B): Seq[NodeInfo] =
    routingTable.findClosestNodes(target)
      .filter(_.id.distance(target) < selfNode.distance(target))

  private def considerDiscoveringNewNodes(): Unit = {
    val tableIsAlmostEmpty = routingTable.nBucketsUsed == 1
    val notSearchingYet = Searches.isInactive
    if (tableIsAlmostEmpty && notSearchingYet) {
      log.info("Performing node search to fill in routing table")
      self ! SearchNode(selfNode)
    }
  }

  private def scheduleCleanup(): Unit =
    context.system.scheduler.schedule(CleanupInterval, CleanupInterval) {
      self ! CleanInactiveSearches
    }(context.dispatcher)

  object Searches extends SearchManager(selfNode) {

    def start(target: Id20B, requester: ActorRef): Unit = {
      val nodes: Seq[NodeInfoOrAddress] = nodesToStart(target)
      if(nodes.isEmpty) log.error("Can't start a search: no starting nodes")
      super.start(target, nodes, requester){
        (nextNode, query) => send(nextNode.asJava, query)
      }
    }

    private def nodesToStart(target: Id20B): Seq[NodeInfoOrAddress] = {
      val nodes: Seq[NodeInfoOrAddress] = routingTable.findClosestNodes(target).map(_.asRight)
      if(nodes.size > routingTable.nodesPerBucket) {
        nodes
      } else {
        log.info("No nodes in routing table to start a search. Will use bootstrap nodes")
        nodes ++ Bootstrap.addresses.map(_.asLeft)
      }
    }

    def continue(trans: TransactionId, origin: NodeInfo, newNodes: Seq[NodeInfo]): Unit =
      super.continue(trans, origin, newNodes){
        (nextNode, query) => send(nextNode.asJava, query)
      }.left.foreach { err =>
        log.warning(s"Can't continue search: $err")
      }

  }

  private def createUdpSocketActor: ActorRef = {
    val props = Props(classOf[UdpSocket], self, port)
    context.actorOf(props, s"udp-socket")
  }

}

case object NodeActor {

  val CleanupInterval: FiniteDuration = 5 minutes

  /**
    * Will cause this actor to discover new nodes and update the routing table.
    * There's no response to be returned.
    */
  case class SearchNode(id: NodeId)

  /**
    * Request from a local actor.
    * Results in [[FoundPeers]]
    */
  case class SearchPeers(hash: InfoHash)

  /**
    * Request from a local actor
    */
  case class StopSearch(target: Id20B)

  /**
    * This actor's response to [[SearchPeers]]
    */
  case class FoundPeers(target: InfoHash, peers: Seq[PeerInfo])

  /**
    * Add this node to routing table.
    * E.g.: It was found in a torrent file
    */
  case class AddNode(node: NodeInfo)

  /**
    * Scheduled by the actor itself to cleanup old request and related transactions
    */
  case object CleanInactiveSearches

}
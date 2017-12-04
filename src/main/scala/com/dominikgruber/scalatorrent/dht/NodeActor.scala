package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.SelfInfo
import com.dominikgruber.scalatorrent.dht.NodeActor._
import com.dominikgruber.scalatorrent.dht.SearchManager.NodeInfoOrAddress
import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._

import scala.concurrent.duration._
import scala.language.postfixOps
import cats.syntax.either._

/**
  * http://www.bittorrent.org/beps/bep_0005.html
  */
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

  /*
    find peers
      d = distance(infohash, node)  for node in local routing table   √
      select x closest nodes, ask each  √
      stop if
        found enough peers; or
        can't find closer node  √
      store (in local routing table?) contact of x responding nodes closest to infohash

    init
      upon inserting first node in table:
        find nodes, every time closer, until can't find more
   */

}

/**
  * A node in a DHT network
  */
case class NodeActor(selfNode: NodeId, port: Int) extends Actor with ActorLogging {


  val routingTable = RoutingTable(selfNode) //TODO persist
  val peerMap = PeerMap() //TODO persist
  lazy val udp: ActorRef = createUdpSocketActor //lazy prevents init before overwrite from test

  val peerSearches = PeersSearches
  val nodeSearches = NodeSearches

  override def preStart(): Unit = {
    udp //trigger lazy init
    scheduleCleanup()
    considerSearchingNewNodes()
  }

  override def receive: Receive = {
    case SearchNode(id) =>
      nodeSearches.start(id)
    case SearchPeers(hash) =>
      peerSearches.start(hash)
    case AddNode(info) =>
      routingTable.add(info)
    case ReceivedFromNode(msg, remote) => msg match { //from UdpSocket
      case q: Query =>
        handleQuery(q, remote)
        updateTable(q.origin, remote)
      case r: Response =>
        handleResponse(r, remote)
        updateTable(r.origin, remote)
      case Error(_, code, message) =>
        log.error(s"Received dht error $code: $message")
    }
    case CleanInactiveSearches =>
      nodeSearches.cleanInactive()
      peerSearches.cleanInactive()
      considerSearchingNewNodes() //maybe a previous node search timed out, let's try again
  }

  private def handleQuery(msg: Query, remote: InetSocketAddress): Unit = msg match {
    case Ping(trans, origin) =>
      send(remote, Pong(trans, selfNode))
    case FindNode(trans, origin, target) =>
      answerFindNode(remote, trans, target)
    case GetPeers(trans, origin, hash) =>
      answerGetPeers(remote, trans, hash)
  }

  private def handleResponse(msg: Response, remote: InetSocketAddress): Unit = msg match {
    case Pong(trans, origin) =>
      //noop: already updated table
    case NodesFound(trans, origin, nodes) =>
      resolveOrigin(origin, remote) {
        nodeSearches.continue(trans, _, nodes)
      }
    case PeersFound(trans, origin, token, peers) =>
      resolveOrigin(origin, remote) {
        reportPeersFound(trans, _, peers)
      }
    case PeersNotFound(trans, origin, token, nodes) =>
      resolveOrigin(origin, remote) {
        peerSearches.continue(trans, _, nodes)
      }
  }

  private def send(remote: InetSocketAddress, message: Message): Unit =
    udp ! SendToNode(message, remote)

  private def updateTable(id: NodeId, addr: InetSocketAddress): Unit =
    NodeInfo.parse(id, addr) match {
      case Right(info) => routingTable.add(info)
      case Left(err) => log.warning(s"Couldn't update routing table: $err")
    }

  private def resolveOrigin(id: NodeId, addr: InetSocketAddress)(handle: NodeInfo => Unit): Unit =
    NodeInfo.parse(id, addr) match {
      case Right(origin) => handle(origin)
      case Left(err) => log.warning(s"Can't resolve origin address: $err")
    }

  private def reportPeersFound(trans: TransactionId, origin: NodeInfo, peers: Seq[PeerInfo]): Unit =
    peerSearches.remember(origin, trans) match {
      case Right(search) =>
        peerMap.add(search.target, peers.toSet)
        search.requester ! NodeActor.FoundPeers(search.target, peers)
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
      send(remote, PeersFound(trans, selfNode, token, peers.toSeq))
    else {
      val nodes = findCloserNodes(hash)
      if (nodes.nonEmpty)
        send(remote, PeersNotFound(trans, selfNode, token, nodes))
    }
  }

  private def findCloserNodes(target: Id20B): Seq[NodeInfo] =
    routingTable.findClosestNodes(target)
      .filter(_.id.distance(target) < selfNode.distance(target))

  private def considerSearchingNewNodes(): Unit = {
    val tableIsAlmostEmpty = routingTable.nBucketsUsed == 1
    val notSearchingYet = nodeSearches.isInactive
    if (tableIsAlmostEmpty && notSearchingYet) {
      log.info("Performing node search to fill in routing table")
      self ! SearchNode(selfNode)
    }
  }

  private def scheduleCleanup(): Unit =
    context.system.scheduler.schedule(CleanupInterval, CleanupInterval) {
      self ! CleanInactiveSearches
    }(context.dispatcher)

  trait Searches[A <: Id20B] extends SearchManager[A] {

    def newMessage(newTrans: TransactionId, target: A): Message

    def start(target: A): Unit = {
      val nodes: Seq[NodeInfoOrAddress] = nodesToStart(target)
      if(nodes.isEmpty) log.error("Can't start a search: no starting nodes")
      super.start(target, sender, nodes){
        (trans, nextNode, _) => send(nextNode.asJava, newMessage(trans, target))
      }
    }

    private def nodesToStart(target: A): Seq[NodeInfoOrAddress] = {
      val nodes: Seq[NodeInfoOrAddress] = routingTable.findClosestNodes(target).map(_.asRight)
      if(nodes.size > routingTable.k) {
        nodes
      } else {
        log.debug("No nodes in routing table to start a search. Will use bootstrap nodes")
        nodes ++ Bootstrap.addresses.map(_.asLeft)
      }
    }

    def continue(trans: TransactionId, origin: NodeInfo, newNodes: Seq[NodeInfo]): Unit =
      super.continue(trans, origin, newNodes){
        (newTrans, nextNode, target) => send(nextNode.asJava, newMessage(newTrans, target))
      }.left.foreach { err =>
        log.warning(s"Can't continue search: $err")
      }

  }
  case object NodeSearches extends Searches[NodeId] {
    override def newMessage(newTrans: TransactionId, target: NodeId): Message =
      FindNode(newTrans, selfNode, target)
  }
  case object PeersSearches extends Searches[InfoHash] {
    override def newMessage(newTrans: TransactionId, target: InfoHash): Message =
      GetPeers(newTrans, selfNode, target)
  }

  private def createUdpSocketActor: ActorRef = {
    val props = Props(classOf[UdpSocket], self, port)
    val nodeIdStr = SelfInfo.nodeId.toString.replace("(", ":").replace(" ", ":").replace(")", "")
    context.actorOf(props, s"udp-socket-$nodeIdStr")
  }

}
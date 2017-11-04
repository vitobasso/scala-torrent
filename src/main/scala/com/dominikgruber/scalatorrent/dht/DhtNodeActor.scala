package com.dominikgruber.scalatorrent.dht

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.dominikgruber.scalatorrent.SelfInfo
import com.dominikgruber.scalatorrent.dht.DhtMessage._
import com.dominikgruber.scalatorrent.dht.DhtNodeActor._
import com.dominikgruber.scalatorrent.dht.UdpSocket.{ReceivedFromNode, SendToNode}

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * http://www.bittorrent.org/beps/bep_0005.html
  */
case object DhtNodeActor {

  val RequestTTL: FiniteDuration = 1 minute
  val CleanupInterval: FiniteDuration = 5 minutes

  /**
    * Identifies a request to a remote node
    */
  case class Transaction(node: NodeId, id: TransactionId)

  /**
    * Remembers an ongoing search requested by a local actor
    */
  case class SearchRequest(target: InfoHash, sender: ActorRef, startedAt: Long)
  case class RequestStatus(closestSoFar: BigInt, lastActivity: Long, fulfilled: Boolean) {
    def isActive: Boolean =
      !fulfilled &&
        (now - lastActivity).millis > RequestTTL
    def updated(newDistance: BigInt): RequestStatus =
      copy(closestSoFar = newDistance, lastActivity = now)
  }
  object RequestStatus {
    def fresh = RequestStatus(Id20B.Max, now, fulfilled = false)
    def fulfilled = RequestStatus(0, now, fulfilled = true)
  }

  /**
    * Request from a local actor
    */
  case class SearchPeers(hash: InfoHash)

  /**
    * Our response to [[SearchPeers]]
    */
  case class FoundPeers(peers: Seq[PeerInfo])

  /**
    * A local actor found it in a torrent file, we should add to routing table
    */
  case class AddNode(node: NodeInfo)

  /**
    * Scheduled by the actor itself to cleanup old request and related transactions
    */
  case object ForgetOldRequests

  implicit class NodeInfoOps(info: NodeInfo) {
    def address: InetSocketAddress = new InetSocketAddress(info.ip.toString, info.port.toInt)
  }

  def now: Long = System.currentTimeMillis

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
        find nodes, every time closer, until can't find mroe
   */

}

case class DhtNodeActor(selfNode: NodeId, udpSender: ActorRef) extends Actor with ActorLogging {

  val table = RoutingTable(SelfInfo.nodeId)

  /**
    * Requests by local actors being currently processed by this actor
    */
  var pendingRequests: Map[SearchRequest, RequestStatus] = Map.empty

  /**
    * [[Transaction]]s from this actor to remote nodes
    * Multiple transactions may be active to fulfill one [[SearchRequest]]
    */
  var pendingTransactions: Map[Transaction, SearchRequest] = Map.empty

  override def preStart(): Unit = {
    scheduleCleanup()
  }

  override def receive: Receive = {
    case SearchPeers(hash) => startPeerSearch(hash)
    case AddNode(info) => table.add(info)
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
    case ForgetOldRequests => cleanup()
  }

  def handleQuery(msg: Query, remote: InetSocketAddress): Receive = {
    case Ping(trans, origin) =>
      send(remote, Pong(trans, selfNode))
    case FindNode(trans, origin, target) =>
      val nodes = table.findClosestNodes(target)
      send(remote, NodesFound(trans, selfNode, nodes))
    case GetPeers(trans, origin, hash) =>
      ??? //TODO store peer info
  }

  def handleResponse(msg: Response, remote: InetSocketAddress): Receive = {
    case Pong(trans, origin) => //noop: already updated table
    case NodesFound(trans, origin, nodes) =>
      nodes.foreach(table.add)
    case PeersFound(trans, origin, token, peers) =>
      endTransaction(origin, trans) match {
        case Right(request) =>
          request.sender ! DhtNodeActor.FoundPeers(peers)
          pendingRequests += (request -> RequestStatus.fulfilled)
          //TODO continue if already has peers. stop when enough peers.
        case Left(err) => log.warning(s"Can't report peers found: $err")
      }
    case PeersNotFound(trans, origin, token, nodes) =>
      val result = for {
        request <- endTransaction(origin, trans).right
        status <- getRequestStatus(request).right
      } yield (request, status)
      result match {
        case Right((request, status)) => continuePeerSearch(request, status, nodes)
        case Left(err) => log.warning(s"Can't continue peer search: $err")
      }
  }

  def send(remote: InetSocketAddress, message: Message): Unit =
    udpSender ! SendToNode(message, remote)

  def updateTable(id: NodeId, addr: InetSocketAddress): Unit =
    NodeInfo.parse(id, addr) match {
      case Right(info) => table.add(info)
      case Left(err) => log.warning(s"Couldn't update routing table: $err")
    }

  def startPeerSearch(hash: InfoHash): Unit =
    table.findClosestNodes(hash).foreach { node =>
      val request = SearchRequest(hash, sender, now)
      beginTransaction(request, node)
      pendingRequests += (request -> RequestStatus.fresh)
    }

  def continuePeerSearch(request: SearchRequest, status: RequestStatus, newNodes: Seq[NodeInfo]): Unit =
    newNodes.filter(_.id.distance(request.target) < status.closestSoFar) match {
      case Seq.empty =>
        log.warning(s"Can't continue peer search for ${request.target}: New nodes aren't closer than before")
      case closerNodes =>
        closerNodes.foreach { beginTransaction(request, _) } //TODO prevent exponential growth: limit pending transactions, hold new closer nodes.
        val newClosest = closerNodes.map(_.id.distance(request.target)).min
        pendingRequests += (request -> status.updated(newClosest))
    }

  def beginTransaction(request: SearchRequest, target: NodeInfo): Unit = {
    val trans = TransactionId.random
    send(target.address, GetPeers(trans, selfNode, request.target))
    pendingTransactions += (Transaction(target.id, trans) -> request)
  }

  def endTransaction(origin: NodeId, trans: TransactionId): Either[String, SearchRequest] = {
    val transaction = Transaction(origin, trans)
    val request = pendingTransactions.get(transaction)
    pendingTransactions -= transaction
    request.toRight(s"$trans was inactive.")
  }

  def getRequestStatus(request: SearchRequest): Either[String, RequestStatus] =
    pendingRequests.get(request)
      .toRight(s"Search request for ${request.target} was inactive.")

  def scheduleCleanup(): Unit =
    context.system.scheduler.schedule(CleanupInterval, CleanupInterval) {
      self ! ForgetOldRequests
    }(context.dispatcher)

  def cleanup(): Unit = {
    val (active, inactive) = pendingRequests.partition {
      case (_, status) => status.isActive
    }
    pendingRequests = active
    pendingTransactions = pendingTransactions.filterNot {
      case (_, request) => inactive contains request
    }
  }

}
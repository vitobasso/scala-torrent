package com.dominikgruber.scalatorrent.dht

import akka.actor.ActorRef
import com.dominikgruber.scalatorrent.dht.SearchManager._
import com.dominikgruber.scalatorrent.dht.message.DhtMessage.{Address, TransactionId, _}
import cats.syntax.either._
import com.dominikgruber.scalatorrent.dht.message.ShortDescription.show

/**
  * Keeps track of ongoing searches and respective pending requests
  */
class SearchManager(selfNodeId: NodeId) {

  /**
    * Searches requested by local actors
    */
  private var searches: Set[Search] = Set.empty

  /**
    * [[Transaction]]s pending response from remote nodes
    * Multiple transactions may belong to each [[Search]]
    */
  private var pending: Map[Transaction, Search] = Map.empty

  type Send = (Address, Query) => Unit

  /**
    * Starts the search by sending queries to given nodes.
    * Keeps track of this search for when handling the response later.
    */
  def start(target: Id20B, startingNodes: Seq[NodeInfoOrAddress], requester: ActorRef)(send: Send): Unit = {
    val search = Search(target, requester)
    searches += search
    startingNodes.foreach { sendAndWait(search, send) }
  }

  /**
    * Validates that we have a pending query to this origin with this [[TransactionId]].
    * Sends new queries to the newly found nodes to continue the search.
    */
  def continue(trans: TransactionId, origin: NodeInfo, nodes: Seq[NodeInfo])(send: Send): Either[String, Unit] =
    for {
      search <- remember(origin, trans).right
    } yield search.closerNodes(origin.id, nodes)
      .map(_.asRight)
      .foreach { sendAndWait(search, send) }

  private def sendAndWait(search: Search, send: Send)(nextNode: NodeInfoOrAddress): Unit = {
    val trans = TransactionId.random
    pending += (Transaction(nextNode.idOrAddress, trans) -> search)
    val query = newQuery(trans, selfNodeId, search.target)
    send(nextNode.address, query)
  }

  /**
    * Validates that we have a pending query to this origin with this [[TransactionId]].
    * @return return the corresponding search state.
    */
  def remember(origin: NodeInfo, trans: TransactionId): Either[String, Search] =
    rememberTransaction(Transaction(origin.id.asRight, trans))
      .orElse { rememberTransaction(Transaction(origin.address.asLeft, trans)) }

  private def rememberTransaction(transaction: Transaction): Either[String, Search] = {
    pending.get(transaction) match {
      case Some(search) =>
        pending -= transaction
        Right(search)
      case None =>
        Left(s"Wasn't expecting ${show(transaction)}.")
    }
  }

  def isInactive: Boolean = pending.isEmpty

  def cleanInactive(): Unit = {
    val (active, inactive) = searches.partition { _.isActive }
    searches = active
    pending = pending.filterNot {
      case (_, search) => inactive contains search
    }
  }
}

object SearchManager {

  /**
    * Identifies a request to a remote node
    */
  case class Transaction(node: NodeIdOrAddress, id: TransactionId)

  type NodeInfoOrAddress = Either[Address, NodeInfo]
  type NodeIdOrAddress = Either[Address, NodeId]

  implicit class NodeInfoOrBootstrapNodeOps(node: NodeInfoOrAddress) {
    def address: Address = node match {
      case Left(bootstrapNode) => bootstrapNode
      case Right(regularNode) => regularNode.address
    }
    def idOrAddress: NodeIdOrAddress = node.right.map(_.id)
  }

  def newQuery(trans: TransactionId, selfNodeId: NodeId, target: Id20B): Query =
    target match {
      case node: NodeId => FindNode(trans, selfNodeId, node)
      case hash: InfoHash => GetPeers(trans, selfNodeId, hash)
    }

}

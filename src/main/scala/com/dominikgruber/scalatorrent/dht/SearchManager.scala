package com.dominikgruber.scalatorrent.dht

import akka.actor.ActorRef
import com.dominikgruber.scalatorrent.dht.SearchManager._
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import cats.syntax.either._

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

}

/**
  * Keeps track of ongoing searches and respective pending requests
  */
class SearchManager[A <: Id20B] {

  /**
    * Searches requested by local actors
    */
  private var searches: Set[Search[A]] = Set.empty

  /**
    * [[Transaction]]s pending response from remote nodes
    * Multiple transactions may belong to each [[Search]]
    */
  private var pending: Map[Transaction, Search[A]] = Map.empty

  type Send = (TransactionId, Address, A) => Unit

  /**
    * Starts the search by sending queries to given nodes.
    * Keeps track of this search for when handling the response later.
    */
  def start(target: A, requester: ActorRef, startingNodes: Seq[NodeInfoOrAddress])(send: Send): Unit = {
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

  private def sendAndWait(search: Search[A], send: Send)(nextNode: NodeInfoOrAddress): Unit = {
    val trans = TransactionId.random
    send(trans, nextNode.address, search.target)
    pending += (Transaction(nextNode.idOrAddress, trans) -> search)
  }

  /**
    * Validates that we have a pending query to this origin with this [[TransactionId]].
    * @return return the corresponding search state.
    */
  def remember(origin: NodeInfo, trans: TransactionId): Either[String, Search[A]] = {
    val transaction = Transaction(origin.id.asRight, trans) //TODO if can't find by id try by address
    val search = pending.get(transaction)
    pending -= transaction
    search.toRight(s"Wasn't expecting $transaction.")
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

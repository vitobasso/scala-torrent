package com.dominikgruber.scalatorrent.dht

import akka.actor.ActorRef
import com.dominikgruber.scalatorrent.dht.NodeActor.Transaction
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._

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
    * Multiple transactions may belong to one [[Search]]
    */
  private var pending: Map[Transaction, Search[A]] = Map.empty


  type Send = (TransactionId, NodeInfo, A) => Unit
  def start(target: A, requestor: ActorRef, startingNodes: Seq[NodeInfo])(send: Send): Unit = {
    val search = Search(target, requestor)
    searches += search
    startingNodes.foreach { sendAndWait(search, send) }
  }

  def continue(trans: TransactionId, origin: NodeId, nodes: Seq[NodeInfo])(send: Send): Either[String, Unit] =
    for {
      search <- remember(origin, trans).right
    } yield search.closerNodes(origin, nodes)
      .foreach { sendAndWait(search, send) }

  private def sendAndWait(search: Search[A], send: Send)(nextNode: NodeInfo): Unit = {
    val trans = TransactionId.random
    send(trans, nextNode, search.target)
    pending += (Transaction(nextNode.id, trans) -> search)
  }

  def remember(origin: NodeId, trans: TransactionId): Either[String, Search[A]] = {
    val transaction = Transaction(origin, trans)
    val search = pending.get(transaction)
    pending -= transaction
    search.toRight(s"Wasn't expecting $transaction.")
  }

  def cleanInactive(): Unit = {
    val (active, inactive) = searches.partition { _.isActive }
    searches = active
    pending = pending.filterNot {
      case (_, search) => inactive contains search
    }
  }
}

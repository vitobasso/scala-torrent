package com.dominikgruber.scalatorrent.dht

import akka.actor.ActorRef
import com.dominikgruber.scalatorrent.dht.Search.RequestStatus
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._

import scala.concurrent.duration.{FiniteDuration, _}
import scala.language.postfixOps

case object Search {

  val TimeToLive: FiniteDuration = 1 minute

  /**
    * @param closestSoFar min distance to a node from which we got a response during this search
    * @param lastActivity last time we got a response for this search in millis
    */
  case class RequestStatus(closestSoFar: BigInt, lastActivity: Long) {
    def isActive: Boolean = (now - lastActivity).millis > TimeToLive
    def updated(newDistance: BigInt): RequestStatus =
      copy(closestSoFar = newDistance, lastActivity = now)
  }
  object RequestStatus {
    def fresh = RequestStatus(Id20B.Max, now)
  }

  def now: Long = System.currentTimeMillis

}

/**
  * Keeps track of a search and the closest distance reached so far
  * @param target [[NodeId]] or [[InfoHash]]
  * @param requester actor listening for results
  */
case class Search[A <: Id20B](target: A, requester: ActorRef) {

  private var status: RequestStatus = RequestStatus.fresh

  def closerNodes(origin: NodeId, newNodes: Seq[NodeInfo]): Seq[NodeInfo] = {
    val closest = status.closestSoFar min origin.distance(target)
    status = status.updated(closest)
    newNodes.filter(_.id.distance(target) < status.closestSoFar)
  }

  def isActive: Boolean = status.isActive

}

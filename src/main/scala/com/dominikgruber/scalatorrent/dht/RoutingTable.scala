package com.dominikgruber.scalatorrent.dht

import com.dominikgruber.scalatorrent.dht.DhtMessage.{Id20B, NodeId, NodeInfo}
import com.dominikgruber.scalatorrent.dht.RoutingTable.{Bucket, NodeEntry, _}

import scala.collection.SortedMap
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

/**
  * http://www.bittorrent.org/beps/bep_0005.html
  *
  * @param k: number of nodes per bucket. must be a power of 2.
  * @param me: id of the node owning this table
  */
case class RoutingTable(me: NodeId, k: Int = DefaultNodesPerBucket) {
  require(isPowerOf2(k))

  private var buckets: SortedMap[BigInt, Bucket] = SortedMap (
    Min -> Bucket(Map.empty, Min, Max)
  )

  /**
    * Adds a node to the table; or
    * If already there, update quality to Good & lastActive to now
    * If bucket is full
    *   and close to "me", then split
    *   otherwise discard
    */
  def add(info: NodeInfo): Unit = {
    val bucket = findBucket(info.id)
    bucket.get(info.id) match {
      case Some(_) =>
        addTo(bucket, info)
      case None =>
        if (bucket.size < k)
          addTo(bucket, info)
        else if(containsMe(bucket) && isWorthSplitting(bucket, info.id)) {
          split(bucket)
          add(info)
        } else {
          replaceOrDiscard(info, bucket)
        }
    }
  }

  def findClosestNodes(id: Id20B): Seq[NodeInfo] =
    findBucket(id)
      .nodes.values
      .map(_.info).toSeq

  private def findBucket(id: Id20B): Bucket =
    buckets.filterKeys(_ <= id).last._2

  private def addTo(bucket: Bucket, info: NodeInfo): Unit = {
    require(bucket.canContain(info.id))
    update(bucket, _ + (info.id -> NodeEntry.fresh(info)))
  }

  private def removeFrom(bucket: Bucket, id: NodeId): Unit = {
    require(bucket.canContain(id))
    update(bucket, _ - id)
  }

  private def update(bucket: Bucket, change: Map[NodeId, NodeEntry] => Map[NodeId, NodeEntry]): Unit = {
    val updatedNodes = change(bucket.nodes)
    val updatedBucket = Bucket(updatedNodes, bucket.min, bucket.max)
    buckets += (bucket.min -> updatedBucket)
  }

  private def containsMe(bucket: Bucket): Boolean = bucket.canContain(me)

  private def isWorthSplitting(bucket: Bucket, newNode: NodeId): Boolean = {
    val ((_, bucket1), (_, bucket2)) = prepareSplit(bucket: Bucket)
    val bucket1Empty = bucket1.nodes.isEmpty && !bucket1.canContain(newNode)
    val bucket2Empty = bucket2.nodes.isEmpty && !bucket2.canContain(newNode)
    !bucket1Empty && !bucket2Empty
  }

  private def split(bucket: Bucket): Unit = {
    val (entry1 @ (key1, _), entry2) = prepareSplit(bucket: Bucket)
    buckets -= key1
    buckets += entry1
    buckets += entry2
  }

  type BucketEntry = (BigInt, Bucket)
  private def prepareSplit(bucket: Bucket): (BucketEntry, BucketEntry) = {
    val mid = (bucket.min + bucket.max) / 2
    val firstHalf = bucket.nodes.filter(_._1 < mid)
    val firstBucket = Bucket(firstHalf, bucket.min, mid)
    val firstEntry = bucket.min -> firstBucket
    val secondHalf = bucket.nodes.filter(_._1 >= mid)
    val secondBucket = Bucket(secondHalf, mid, bucket.max)
    val secondEntry = mid -> secondBucket
    (firstEntry, secondEntry)
  }

  private def replaceOrDiscard(node: NodeInfo, bucket: Bucket): Unit =
    findWorst(bucket) match {
      case Some((_, Good)) =>
        //noop: discard the new node
      case Some((worst, _)) =>
        removeFrom(bucket, worst)
        addTo(bucket, node)
      case None =>
        //noop: unexpected, shouldn't have called this function if bucket is empty
    }

  private def findWorst(bucket: Bucket): Option[(NodeId, Quality)] = {
    val order: Ordering[(Quality, Long)] = Ordering.Tuple2(QualityOrder.reverse, Ordering.Long.reverse)
    bucket.nodes.toList
      .sortBy { case (_, NodeEntry(_, quality, lastAcive)) => (quality, lastAcive) }(order)
      .lastOption //min quality, min lastActive
      .map { case (id, NodeEntry(_, quality, _)) => (id, quality) }
  }

}

object RoutingTable {

  val DefaultNodesPerBucket = 8
  val TimeToInactiveNode: Duration = 15 minutes
  val TimeToRefreshBucket: Duration = 15 minutes
  val Min: BigInt = BigInt(0)
  val Max: BigInt = BigInt(2).pow(160)

  sealed trait Quality
  case object Good extends Quality
  case object Questionable extends Quality
  case object Bad extends Quality

  val QualityOrder: Ordering[Quality] = {
    val ranking: Map[Quality, Int] = Seq(Bad, Questionable, Good).zipWithIndex.toMap
    Ordering.by(ranking)
  }

  case class NodeEntry(info: NodeInfo, quality: Quality, lastAcive: Long) //TODO lastActive implies quality
  object NodeEntry {
    def fresh(info: NodeInfo) = NodeEntry(info, Good, System.currentTimeMillis)
  }

  /**
    * @param min inclusive
    * @param max exclusive
    * @todo lastChanged
    */
  case class Bucket(nodes: Map[NodeId, NodeEntry], min: BigInt, max: BigInt) {
    def size: Int = nodes.size
    def get(node: NodeId): Option[NodeEntry] = nodes.get(node)
    def contains(node: NodeId): Boolean = get(node).isDefined
    def canContain(node: NodeId): Boolean = min <= node && node < max
  }

  implicit def toBigInt(id: Id20B): BigInt = id.toBigInt

  def isPowerOf2(i: Int): Boolean = (i & -i) == i

  /*
    routing table
      status
        good
          responded to us in the last 15 min; or
          sent us a request in the last 15 min and has ever responded to us anytime in the past
        questionable:
          15 min inactivity
        bad
          failed to respond multiple queries in a row
      keep only good nodes (keep questionable until replacing by a enw good one?)
      priority to good nodes

      each bucket
        holds K=8 nodes

        when full,
          if contains our own nodeid, split in half the range
          else, discard
            1. bad
            2. questionable (the one inactive for longer, but first try to ping 2x)
            3. the new one
        last changed
          refresh after 15min

     begin w/ new table
      1 bucket 0 to 2^160
      find_node closer and closer till can't find
     start
      > p2p handshake
      < port
      > ping
      save peer
  */

}
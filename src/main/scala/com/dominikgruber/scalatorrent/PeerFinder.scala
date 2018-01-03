package com.dominikgruber.scalatorrent

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.PeerFinder._
import com.dominikgruber.scalatorrent.SelfInfo.selfPeerId
import com.dominikgruber.scalatorrent.dht.NodeActor
import com.dominikgruber.scalatorrent.dht.NodeActor.{SearchPeers, StopSearch}
import com.dominikgruber.scalatorrent.dht.message.DhtMessage.InfoHash
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.tracker.http.HttpTracker.SendEventStarted
import com.dominikgruber.scalatorrent.tracker.http.{HttpTracker, TrackerResponseWithFailure, TrackerResponseWithSuccess}
import com.dominikgruber.scalatorrent.tracker.udp.UdpTracker

import scala.util.matching.Regex

object PeerFinder {
  case object FindPeers
  case class PeersFound(addresses: Set[PeerAddress])

  sealed trait PeerStatus
  case object New extends PeerStatus
  case object Connected extends PeerStatus
  case object Closed extends PeerStatus
}

/**
  * Finds peers via trackers (HTTP, UDP) or the DHT
  *
  * @param peerPortIn: local port listening to BitTorrent messages
  * @param nodePortIn: local port listening to DHT messages
  * @param torrent: actor interested in finding peers
  */
case class PeerFinder(meta: MetaInfo, peerPortIn: Int, nodePortIn: Int, torrent: ActorRef) extends Actor with ActorLogging {

  val hash: InfoHash = torrentHash(meta)

  //lazy prevents init before overwrite from test
  lazy val trackers: Seq[ActorRef] = trackerAddrs.flatMap { createTrackerActor }
  lazy val node: ActorRef = createNodeActor
  var isActive = false

  /**
    * Will try to find new peers while haven't found at least the target num.
    */
  var peersKnown: Map[PeerAddress, PeerStatus] = Map.empty

  override def receive: Receive = {
    case FindPeers => // from Torrent
      start()
    case NodeActor.FoundPeers(_, peers) => // from NodeActor
      handleResult(peers.map(PeerAddress.fromDhtAddress), "DHT")
      if(knowEnoughPeers && isActive) {
        stop()
      }
    case s: TrackerResponseWithSuccess => // from Tracker
      handleResult(s.peers.map(_.address), "tracker")
    case f: TrackerResponseWithFailure =>
      log.warning(s"Request to Tracker failed: ${f.reason}")
    case Torrent.PeerConnected(peer) =>
      log.debug(s"Peer connected: $peer")
      peersKnown = peersKnown.updated(peer, Connected)
    case Torrent.PeerClosed(peer, cause) =>
      log.debug(s"Peer closed: $peer. Cause: $cause")
      peersKnown = peersKnown.updated(peer, Closed)
      if(!knowEnoughPeers && !isActive) {
        start()
      }
  }

  private def start(): Unit = {
    log.info(s"Starting peer search on DHT and trackers")
    isActive = true
    trackers.foreach { _ ! SendEventStarted(0, 0) }
    node ! SearchPeers(hash)
  }

  private def stop(): Unit = {
    log.info(s"Stopping peer search on DHT")
    isActive = false
    node ! StopSearch(hash)
  }

  private def handleResult(unfiltered: Iterable[PeerAddress], source: String): Unit = {
    val uniquePeers: Set[PeerAddress] = unfiltered.toSet
    val newPeers: Set[PeerAddress] = uniquePeers.diff(peersKnown.keySet)
    logResult(source, unfiltered, newPeers)
    peersKnown = peersKnown ++ newPeers.map(_ -> New)
    if(newPeers.nonEmpty) torrent ! PeersFound(newPeers)
  }

  private def logResult(source: String, unfiltered: Iterable[PeerAddress], newPeers: Set[PeerAddress]): Unit = {
    val discarded = unfiltered.size - newPeers.size
    val newPeersMsg = s"Found ${newPeers.size} new peers from $source: ${newPeers.mkString(", ")}."
    val discardedMsg = s"Discarded $discarded previously known or duplicates."
    log.info(s"$newPeersMsg $discardedMsg")
  }
  val targetNumPeers = 50 //TODO config
  def knowEnoughPeers: Boolean = {
    val workingPeers = peersKnown.count { case (_, status) => status != PeerFinder.Closed }
    workingPeers >= targetNumPeers
  }

  private def createTrackerActor(peerUrl: String): Option[ActorRef] = {
    val url: Regex = """(\w+)://(.*):(\d+)""".r
    val props = peerUrl match {
      case url("http", _, _) =>
        Some(Props(classOf[HttpTracker], meta, selfPeerId, peerPortIn)) //TODO rm selfPeerId param
      case url("udp", host, port) =>
        Some(Props(classOf[UdpTracker], meta.fileInfo, new InetSocketAddress(host, port.toInt), peerPortIn))
      case _ => None
    }
    val escapedUrl = peerUrl.replaceAll("/", "_")
    props.map {
      context.actorOf(_, s"tracker-$escapedUrl")
    }
  }

  private def createNodeActor: ActorRef = {
    val props = Props(classOf[NodeActor], SelfInfo.nodeId, nodePortIn)
    context.actorOf(props, s"dht-node")
  }

  private def trackerAddrs: Seq[String] =
    meta.announceList.map { _.flatten }.getOrElse(Seq(meta.announce))

  def torrentHash(meta: MetaInfo): InfoHash = {
    val hex = meta.hash.grouped(2).mkString(" ")
    InfoHash.validateHex(hex) match { //TODO validate earlier on MetaInfo creation
      case Right(hash) => hash
      case Left(err) =>
        val msg = s"Invalid torrent hash: $err"
        log.error(msg)
        sys.error(msg)
    }
  }

}

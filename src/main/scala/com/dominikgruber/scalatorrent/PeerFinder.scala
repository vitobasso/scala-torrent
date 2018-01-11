package com.dominikgruber.scalatorrent

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.PeerFinder._
import com.dominikgruber.scalatorrent.cli.CliActor.ReportPlease
import com.dominikgruber.scalatorrent.dht.NodeActor
import com.dominikgruber.scalatorrent.dht.NodeActor.{SearchPeers, StopSearch}
import com.dominikgruber.scalatorrent.dht.message.DhtMessage.InfoHash
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.tracker.http.HttpTracker.SendEventStarted
import com.dominikgruber.scalatorrent.tracker.http.{HttpTracker, TrackerResponseWithFailure, TrackerResponseWithSuccess}
import com.dominikgruber.scalatorrent.tracker.udp.UdpTracker

import scala.util.matching.Regex

/**
  * Finds peers via trackers (HTTP, UDP) or the DHT
  *
  * @param torrent: actor interested in finding peers
  */
case class PeerFinder(meta: MetaInfo, torrent: ActorRef, config: Config) extends Actor with ActorLogging {

  val hash: InfoHash = torrentHash(meta)

  //lazy prevents init before overwrite from test
  lazy val trackers: Seq[ActorRef] = trackerAddrs.flatMap { createTrackerActor }
  lazy val node: ActorRef = createNodeActor
  var isActive = false

  /**
    * Will try to find new peers while haven't found at least the target num.
    */
  var peersKnown: Map[PeerAddress, PeerStatus] = Map.empty

  override def receive: Receive =
    findingPeers orElse
      updatingPeerStatus orElse
      reporting

  private def findingPeers: Receive = {
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

  }

  private def updatingPeerStatus: Receive = {
    case Torrent.PeerConnected(peer) =>
      log.debug(s"Peer connected: $peer")
      peersKnown = peersKnown.updated(peer, Connected)
    case Torrent.PeerClosed(peer, cause) =>
      log.debug(s"Peer closed: $peer. Cause: $cause")
      peersKnown = peersKnown.updated(peer, Dead)
      if(!knowEnoughPeers && !isActive) {
        start()
      }
  }

  private def reporting: Receive = {
    case ReportPlease(listener) => // from Torrent
      listener ! PeersReport(peerCounts, isActive)
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
    peersKnown = peersKnown ++ newPeers.map(_ -> Trying)
    if(newPeers.nonEmpty) torrent ! PeersFound(newPeers)
  }

  private def logResult(source: String, unfiltered: Iterable[PeerAddress], newPeers: Set[PeerAddress]): Unit = {
    val discarded = unfiltered.size - newPeers.size
    val newPeersMsg = s"Found ${newPeers.size} new peers from $source: ${newPeers.mkString(", ")}."
    val discardedMsg = s"Discarded $discarded previously known or duplicates."
    log.info(s"$newPeersMsg $discardedMsg")
  }
  def knowEnoughPeers: Boolean = {
    val workingPeers = peersKnown.count { case (_, status) => status != PeerFinder.Dead }
    workingPeers >= config.targetNumPeers
  }

  def peerCounts: Map[PeerStatus, Int] =
    peersKnown
      .groupBy { case (_, status) => status }
      .mapValues(_.size)

  private def createTrackerActor(peerUrl: String): Option[ActorRef] = {
    val url: Regex = """(\w+)://(.*):(\d+)""".r
    val props = peerUrl match {
      case url("http", _, _) =>
        Some(HttpTracker.props(meta, config.tracker))
      case url("udp", host, port) =>
        val remote = new InetSocketAddress(host, port.toInt)
        val props = UdpTracker.props(meta.fileInfo, remote, config.tracker)
        Some(props)
      case _ => None
    }
    val escapedUrl = peerUrl.replaceAll("/", "_")
    props.map {
      context.actorOf(_, s"tracker-$escapedUrl")
    }
  }

  private def createNodeActor: ActorRef = {
    val props = NodeActor.props(config.node)
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

object PeerFinder {

  case class TrackerConfig(peerId: String, portIn: Int)
  /**
    * @param targetNumPeers we shall keep searching for peers while we don't have that number of good ones
    * @param tracker bittorrent protocol config
    * @param node dht protocol config
    */
  case class Config(targetNumPeers: Int, tracker: TrackerConfig, node: NodeActor.Config)
  def props(meta: MetaInfo, torrent: ActorRef, config: Config) =
    Props(classOf[PeerFinder], meta, torrent, config)

  case object FindPeers
  case class PeersFound(addresses: Set[PeerAddress])

  /**
    * @param counts peer counts per [[PeerStatus]]
    * @param isActive is actively searching for peers
    */
  case class PeersReport(counts: Map[PeerStatus, Int], isActive: Boolean)

  sealed trait PeerStatus
  case object Connected extends PeerStatus
  case object Trying extends PeerStatus
  case object Dead extends PeerStatus
}

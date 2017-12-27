package com.dominikgruber.scalatorrent

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.PeerFinder._
import com.dominikgruber.scalatorrent.SelfInfo.selfPeerId
import com.dominikgruber.scalatorrent.dht.NodeActor
import com.dominikgruber.scalatorrent.dht.NodeActor.{FoundPeers, SearchPeers, StopSearch}
import com.dominikgruber.scalatorrent.dht.message.DhtMessage.InfoHash
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.tracker.http.HttpTracker.{SendEventStarted, TrackerConnectionFailed}
import com.dominikgruber.scalatorrent.tracker.http.{HttpTracker, TrackerResponseWithFailure, TrackerResponseWithSuccess}
import com.dominikgruber.scalatorrent.tracker.udp.UdpTracker

import scala.util.matching.Regex

object PeerFinder {
  case object FindPeers
  case class PeersFound(addresses: Set[PeerAddress])
}

/**
  * Finds peers via trackers (HTTP, UDP) or the DHT
  *
  * @param peerPortIn: local port listening to BitTorrent messages
  * @param nodePortIn: local port listening to DHT messages
  * @param torrent: actor interested in finding peers
  */
case class PeerFinder(meta: MetaInfo, peerPortIn: Int, nodePortIn: Int, torrent: ActorRef) extends Actor with ActorLogging {

  //lazy prevents init before overwrite from test
  lazy val trackers: Seq[ActorRef] = trackerAddrs.flatMap { createTrackerActor }
  lazy val node: ActorRef = createNodeActor

  /**
    * Will try to find new peers while haven't found at least the target num.
    */
  val targetNumPeers = 20 //TODO config
  var peersKnown: Set[PeerAddress] = Set.empty

  override def receive: Receive = {
    case FindPeers => // from Torrent
      handleRequest()
    case FoundPeers(_, peers) => // from NodeActor
      handleResult(peers.map(PeerAddress.fromDhtAddress), "DHT")
      if(peersKnown.size >= targetNumPeers) node ! StopSearch
    case s: TrackerResponseWithSuccess => // from Tracker
      handleResult(s.peers.map(_.address), "tracker")
    case f: TrackerResponseWithFailure =>
      log.warning(s"Request to Tracker failed: ${f.reason}")
    case TrackerConnectionFailed(msg) =>
      log.warning(s"Connection to Tracker failed: $msg")
  }

  private def handleRequest() = {
    trackers.foreach { _ ! SendEventStarted(0, 0) }
    val hex = meta.hash.grouped(2).mkString(" ")
    InfoHash.validateHex(hex) match { //TODO validate earlier on MetaInfo creation
      case Right(hash) => node ! SearchPeers(hash)
      case Left(err) => log.error(s"Invalid torrent hash: $err")
    }
  }

  private def handleResult(unfiltered: Iterable[PeerAddress], source: String): Unit = {
    val uniquePeers: Set[PeerAddress] = unfiltered.toSet
    val newPeers: Set[PeerAddress] = uniquePeers.diff(peersKnown)
    logResult(source, unfiltered, newPeers)
    peersKnown = peersKnown ++ newPeers
    torrent ! PeersFound(newPeers)
  }

  private def logResult(source: String, unfiltered: Iterable[PeerAddress], newPeers: Set[PeerAddress]): Unit = {
    val discarded = unfiltered.size - newPeers.size
    val newPeersMsg = s"Found ${newPeers.size} new peers from $source. ${newPeers.mkString(", ")}."
    val discardedMsg = s"Discarded $discarded previously known or duplicates."
    log.info(s"$newPeersMsg $discardedMsg")
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

}

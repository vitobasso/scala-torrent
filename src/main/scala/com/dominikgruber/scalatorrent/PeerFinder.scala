package com.dominikgruber.scalatorrent

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.PeerFinder.{FindPeers, PeersFound}
import com.dominikgruber.scalatorrent.SelfInfo.selfPeerId
import com.dominikgruber.scalatorrent.dht.NodeActor
import com.dominikgruber.scalatorrent.dht.NodeActor.{FoundPeers, SearchPeers}
import com.dominikgruber.scalatorrent.dht.message.DhtMessage.{InfoHash, PeerInfo}
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

case class PeerFinder(meta: MetaInfo, portIn: Int, torrent: ActorRef) extends Actor with ActorLogging {

  //lazy prevents init before overwrite from test
  lazy val trackers: Seq[ActorRef] = trackerAddrs.flatMap { createTrackerActor }
  lazy val node: ActorRef = createNodeActor

  override def receive: Receive = {
    case FindPeers =>
      trackers.foreach { _ ! SendEventStarted(0, 0) }
      InfoHash.validate(meta.hash) match { //TODO validate earlier on MetaInfo creation
        case Right(hash) => node ! SearchPeers(hash)
        case Left(err) => log.error(s"Invalid torrent hash: $err")
      }

    case FoundPeers(_, peers) => // from NodeActor
      def getAddress(info: PeerInfo): PeerAddress = PeerAddress(info.ip.toString, info.port.toInt)
      val peerAddresses = peers.map(getAddress).toSet
      torrent ! PeersFound(peerAddresses)

    case s: TrackerResponseWithSuccess => // from Tracker
      log.debug(s"Request to Tracker successful: $s")
      val uniqueAddresses: Set[PeerAddress] = s.peers.map(_.address).toSet
      torrent ! PeersFound(uniqueAddresses)

    case f: TrackerResponseWithFailure =>
      log.warning(s"Request to Tracker failed: ${f.reason}")

    case TrackerConnectionFailed(msg) =>
      log.warning(s"Connection to Tracker failed: $msg")
  }

  private def createTrackerActor(peerUrl: String): Option[ActorRef] = {
    val url: Regex = """(\w+)://(.*):(\d+)""".r
    val props = peerUrl match {
      case url("http", _, _) =>
        Some(Props(classOf[HttpTracker], meta, selfPeerId, portIn)) //TODO rm selfPeerId param
      case url("udp", host, port) =>
        Some(Props(classOf[UdpTracker], meta.fileInfo, new InetSocketAddress(host, port.toInt)))
      case _ => None
    }
    val escapedUrl = peerUrl.replaceAll("/", "_")
    props.map {
      context.actorOf(_, s"tracker-$escapedUrl-${meta.hash}")
    }
  }

  private def createNodeActor: ActorRef = {
    val props = Props(classOf[NodeActor], SelfInfo.nodeId)
    context.actorOf(props, s"node-actor-${SelfInfo.nodeId}")
  }

  private def trackerAddrs: Seq[String] =
    meta.announceList.map { _.flatten }.getOrElse(Seq(meta.announce))

}

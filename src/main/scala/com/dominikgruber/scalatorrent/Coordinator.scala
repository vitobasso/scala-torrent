package com.dominikgruber.scalatorrent

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.dominikgruber.scalatorrent.Coordinator._
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.HandshakeActor.TorrentInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.network.ConnectionManager.CreateConnection
import com.dominikgruber.scalatorrent.peerwireprotocol.network.PeerConnection.SetListener
import com.dominikgruber.scalatorrent.peerwireprotocol.network._
import com.dominikgruber.scalatorrent.peerwireprotocol.{InboundHandshake, OutboundHandshake}
import com.dominikgruber.scalatorrent.tracker.{Peer, PeerAddress}
import com.dominikgruber.scalatorrent.util.{Asking, ExtraPattern}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable

object Coordinator {
  case class AddTorrentFile(file: String)
  case class TorrentAddedSuccessfully(file: String, torrent: ActorRef)
  case class TorrentFileInvalid(file: String, message: String)
  case class ConnectToPeer(address: PeerAddress, meta: MetaInfo)
  case class PeerConnected(peerConn: ActorRef, address: PeerAddress)
  case class IdentifyTorrent(infoHash: String)
}

class Coordinator extends Actor with ActorLogging with Asking {

  val conf: Config = ConfigFactory.load.getConfig("scala-torrent")
  val peerPort: Int = conf.getInt("bittorrent-port ")
  val nodePort: Int = conf.getInt("dht-port ")
  val connManager: ActorRef = createConnManagerActor(peerPort)

  val torrents = mutable.Map.empty[String,(ActorRef, MetaInfo)]

  override def receive: Receive = {

    case AddTorrentFile(file) => // from Boot
      addTorrentFile(file)

    case ConnectToPeer(peer, meta) => // from Torrent
      createConnRequestTempActor(peer, meta, sender)

    case PeerConnected(peerConn, address) => // inbound, from ConnectionManager
      val handshakeActor = createInboundHandshakeActor(peerConn, address)
      peerConn ! SetListener(handshakeActor)

    case IdentifyTorrent(infoHash) => // from PeerConnection
      torrents.get(infoHash) match {
        case Some((torrent, meta)) =>
          sender ! TorrentInfo(meta, torrent)
        case None => //TODO handle not found
      }
  }

  private def addTorrentFile(file: String): Unit = {
    try {
      val meta = MetaInfo(new File(file))
      val torrentActor: ActorRef = createTorrentActor(meta)
      torrents(meta.hash) = (torrentActor, meta)
      sender ! TorrentAddedSuccessfully(file, torrentActor)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        sender ! TorrentFileInvalid(file, s"${e.getClass.getName}: ${e.getMessage}")
    }
  }

  private def createTorrentActor(meta: MetaInfo) = {
    val torrentProps = Props(classOf[Torrent], meta, self, peerPort, nodePort)
    context.actorOf(torrentProps, "torrent-" + meta.hash)
  }

  private def createOutboundHandshakeActor(peerConn: ActorRef, address: PeerAddress, meta: MetaInfo, torrent: ActorRef): ActorRef = {
    val props = Props(classOf[OutboundHandshake], peerConn, address, meta, torrent)
    context.actorOf(props, s"handshake-out-$address-${meta.hash}")
  }

  private def createInboundHandshakeActor(peerConn: ActorRef, address: PeerAddress): ActorRef = {
    val props = Props(classOf[InboundHandshake], peerConn, address)
    context.actorOf(props, s"handshake-in-$address")
  }

  private def createConnManagerActor(peerPort: Int): ActorRef = {
    val props = Props(classOf[ConnectionManager], peerPort)
    context.actorOf(props, "connection-manager")
  }

  private def createConnRequestTempActor(peer: PeerAddress, meta: MetaInfo, torrent: ActorRef): ActorRef = {
    val props = Props(new PeerConnRequestActor(peer, meta, torrent))
    val name = s"temp-peer-connection-request-$peer-${meta.hash}"
    context.child(name) match { //TODO also check existing (established) PeerConnection?
      case Some(existing) => existing
      case None => context.actorOf(props, name)
    }
  }

  class PeerConnRequestActor(peer: PeerAddress, meta: MetaInfo, torrent: ActorRef)
    extends Actor with ActorLogging with ExtraPattern {
    connManager ! CreateConnection(peer)
    override def receive: Receive = {
      case PeerConnected(peerConn, address) => // outbound, from ConnectionManager
        val handshakeActor = createOutboundHandshakeActor(peerConn, address, meta, torrent)
        peerConn ! SetListener(handshakeActor)
        done()
    }
  }

}
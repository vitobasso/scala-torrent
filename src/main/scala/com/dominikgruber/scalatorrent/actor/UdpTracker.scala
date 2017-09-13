package com.dominikgruber.scalatorrent.actor

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.ISO_8859_1

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.{IO, UdpConnected}
import akka.util.ByteString
import com.dominikgruber.scalatorrent.actor.Tracker.SendEventStarted
import com.dominikgruber.scalatorrent.metainfo.{FileMetaInfo, SelfInfo}
import com.dominikgruber.scalatorrent.tracker.UDPMessages.{AnnounceRequest, AnnounceResponse, ConnectRequest, ConnectResponse, TransactionId}
import com.dominikgruber.scalatorrent.tracker.{Peer, TrackerResponseWithSuccess, UDPMessages}
import com.typesafe.config.ConfigFactory

import scala.util.{Failure, Random, Success}

case class UdpTracker(meta: FileMetaInfo, remote: InetSocketAddress) extends Actor with ActorLogging {

  val udpManager: ActorRef = IO(UdpConnected)(context.system)

  override def preStart(): Unit = {
    udpManager ! UdpConnected.Connect(self, remote)
  }

  override def receive: Receive = {
    case UdpConnected.Connected =>
      context become ready(sender)
  }

  def ready(udpConn: ActorRef): Receive = {
    case SendEventStarted(dl, ul) => // from Torrent
      val transactionId = TransactionId(Random.nextInt())
      val connReq = ConnectRequest(transactionId)
      val connReqBytes = ByteString(UDPMessages.encode(connReq))
      udpConn ! UdpConnected.Send(connReqBytes)
      context become expectingConnectResponse(sender, udpConn, transactionId, dl, ul)

    case UdpConnected.Disconnect =>
      udpConn ! UdpConnected.Disconnect
      context.become(disconnecting)
  }

  def expectingConnectResponse(requestor: ActorRef, udpConn: ActorRef, trans: TransactionId, dl: Long, ul: Long): Receive = {
    case UdpConnected.Received(data) =>
      val torrentHash = UDPMessages.InfoHash(meta.infoHash.toArray)
      val peerId = UDPMessages.PeerId(SelfInfo.selfPeerId.getBytes(ISO_8859_1))
      val left: Long = meta.totalBytes //TODO get progress
      val key = 123L //TODO
      UDPMessages.decode(data.toArray) match {
        case Success(c: ConnectResponse) =>
          val announceReq = AnnounceRequest(
            c.conn, trans, torrentHash, peerId, dl, left, ul, UDPMessages.Started, key = key, port = port)
          val announceReqBytes = ByteString(UDPMessages.encode(announceReq))
          udpConn ! UdpConnected.Send(announceReqBytes)
        case Success(a: AnnounceResponse) =>
          //TODO validate transaction
          val peers: List[Peer] = a.peers.map(p => Peer(None, ipAsString(p.ip), p.tcpPort)).toList
          requestor ! TrackerResponseWithSuccess(a.interval, None, None, a.seeders, a.leechers, peers, None)
        case Failure(t) =>
          log.error(t, "Request to tracker failed")
      }
  }

  def ipAsString(ip: Int): String = ByteBuffer.allocate(4).putInt(ip).array().mkString(".")
  val port: Int = ConfigFactory.load.getConfig("scala-torrent").getInt("port") //TODO

  def disconnecting: Receive = {
    case UdpConnected.Disconnected => context.stop(self)
  }

}
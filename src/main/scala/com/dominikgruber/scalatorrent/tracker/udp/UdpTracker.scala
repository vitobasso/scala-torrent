package com.dominikgruber.scalatorrent.tracker.udp

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.ISO_8859_1

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.io.{IO, UdpConnected}
import akka.util.ByteString
import com.dominikgruber.scalatorrent.SelfInfo
import com.dominikgruber.scalatorrent.metainfo.FileMetaInfo
import com.dominikgruber.scalatorrent.tracker.Peer
import com.dominikgruber.scalatorrent.tracker.http.HttpTracker.{SendEventStarted, TrackerConnectionFailed}
import com.dominikgruber.scalatorrent.tracker.http.TrackerResponseWithSuccess
import com.dominikgruber.scalatorrent.tracker.udp.UdpEncoding._
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
      val connReqBytes = ByteString(encode(connReq))
      log.debug(s"Sending $connReq")
      udpConn ! UdpConnected.Send(connReqBytes)
      context become expectingConnectResponse(sender, udpConn, transactionId, dl, ul)

    case UdpConnected.Disconnect =>
      udpConn ! UdpConnected.Disconnect
      context.become(disconnecting)
  }

  def expectingConnectResponse(requester: ActorRef, udpConn: ActorRef, trans: TransactionId, dl: Long, ul: Long): Receive = {
    case UdpConnected.Received(data) =>
      val torrentHash = InfoHash.validate(meta.infoHash.toArray)
      val peerId = PeerId.validate(SelfInfo.selfPeerId.getBytes(ISO_8859_1))
      val left: Long = meta.totalBytes //TODO get progress
      val key = 123L //TODO
      decode(data.toArray) match {
        case Success(c: ConnectResponse) =>
          log.debug(s"Received $c")
          val announceReq = AnnounceRequest(
            c.conn, trans, torrentHash, peerId, dl, left, ul, AnnounceEvent.Started, key = key, port = port)
          val announceReqBytes = ByteString(encode(announceReq))
          log.debug(s"Sending $announceReq")
          udpConn ! UdpConnected.Send(announceReqBytes)
        case Success(a: AnnounceResponse) =>
          log.debug(s"Received $a")
          //TODO validate transaction
          val peers: List[Peer] = a.peers.map(p => Peer(None, p.ip, p.port)).toList
          requester ! TrackerResponseWithSuccess(a.interval, None, None, a.seeders, a.leechers, peers, None)
        case Failure(t) =>
          log.error(t, "Request to tracker failed")
          requester ! TrackerConnectionFailed(t.getMessage)
      }
  }

  val port: Int = ConfigFactory.load.getConfig("scala-torrent").getInt("port") //TODO

  def disconnecting: Receive = {
    case UdpConnected.Disconnected => context.stop(self)
  }

}
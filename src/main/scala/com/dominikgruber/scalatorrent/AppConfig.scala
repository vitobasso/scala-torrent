package com.dominikgruber.scalatorrent

import java.time

import com.dominikgruber.scalatorrent.PeerFinder.TrackerConfig
import com.dominikgruber.scalatorrent.cli.CliActor
import com.dominikgruber.scalatorrent.dht.NodeActor
import com.dominikgruber.scalatorrent.peerwireprotocol.{HandshakeActor, TransferState}
import com.dominikgruber.scalatorrent.peerwireprotocol.network.ConnectionManager
import com.typesafe.config.{Config, ConfigFactory}
import AppConfig._

import scala.concurrent.duration._

case class AppConfig(conf: Config) {

  val bitTorrent = BitTorrentConfig(
    conf.getString("bittorrent-client-id"),
    conf.getInt("bittorrent-tcp-pipelining"),
    conf.getDuration("bittorrent-request-ttl").toMillis.millis,
    conf.getInt("bittorrent-port ")
  )
  val dhtPort: Int = conf.getInt("dht-port ")
  val tcpTimeout: FiniteDuration = conf.getDuration("tcp-timeout").toMillis.millis
  val targetNumPeers: Int = conf.getInt("target-num-peers")
  val cliRefreshRate: time.Duration = conf.getDuration("cli-refresh-rate")

  val self = SelfInfo(bitTorrent.clientId)

  val tracker = TrackerConfig(self.peerId, bitTorrent.port)
  val node = NodeActor.Config(self.nodeId, dhtPort)
  val peerFinder = PeerFinder.Config(targetNumPeers, tracker, node)
  val transferState = TransferState.Config(bitTorrent.tcpPipelining, bitTorrent.requestTtl)
  val torrent = Torrent.Config(transferState, peerFinder)
  val cli = CliActor.Config(cliRefreshRate.toMillis.millis)
  val connMan = ConnectionManager.Config(bitTorrent.port, tcpTimeout)
  val handshake = HandshakeActor.Config(self.pstr, self.extension, self.peerId)
  val coordinator = Coordinator.Config(cli.refreshRate, connMan, handshake, torrent)

}

object AppConfig {
  def load = AppConfig(
    ConfigFactory.load.getConfig("scala-torrent")
  )
  case class BitTorrentConfig(clientId: String, tcpPipelining: Int, requestTtl: Duration, port: Int)
}


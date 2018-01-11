package com.dominikgruber.scalatorrent

import com.dominikgruber.scalatorrent.PeerFinder.TrackerConfig
import com.dominikgruber.scalatorrent.cli.CliActor
import com.dominikgruber.scalatorrent.dht.NodeActor
import com.dominikgruber.scalatorrent.peerwireprotocol.HandshakeActor
import com.dominikgruber.scalatorrent.peerwireprotocol.network.ConnectionManager
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

case class AppConfig(conf: Config) {

  val bitTorrent = BitTorrentConfig(
    conf.getString("bittorrent-client-id"),
    conf.getDuration("bittorrent-request-ttl").toMillis.millis,
    conf.getInt("bittorrent-port ")
  )

  val dht = DhtConfig(
    conf.getInt("dht-port ")
  )

  val tcp = TcpConfig(
    conf.getDuration("tcp-timeout").toMillis.millis
  )

  val self = SelfInfo(bitTorrent.clientId)

  val tracker = TrackerConfig(self.peerId, bitTorrent.port)
  val node = NodeActor.Config(self.nodeId, dht.port)
  val peerFinder = PeerFinder.Config(conf.getInt("target-num-peers"), tracker, node)
  val torrent = Torrent.Config(bitTorrent.requestTtl, peerFinder)
  val cli = CliActor.Config(conf.getDuration("cli-refresh-rate").toMillis.millis)
  val connMan = ConnectionManager.Config(bitTorrent.port, tcp.timeout)
  val handshake = HandshakeActor.Config(self.pstr, self.extension, self.peerId)
  val coordinator = Coordinator.Config(bitTorrent.port, cli.refreshRate, connMan, handshake, torrent)

}

object AppConfig {
  def load = AppConfig(
    ConfigFactory.load.getConfig("scala-torrent")
  )
}

case class BitTorrentConfig(clientId: String, requestTtl: Duration, port: Int)
case class DhtConfig(port: Int)
case class TcpConfig(timeout: FiniteDuration)


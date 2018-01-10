package com.dominikgruber.scalatorrent

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

object AppConfig {

  private val conf: Config = ConfigFactory.load.getConfig("scala-torrent")

  val peerPort: Int = conf.getInt("bittorrent-port ")
  val nodePort: Int = conf.getInt("dht-port ")

  val bittorrentRequestTtl: Duration = conf.getDuration("bittorrent-request-ttl").toMillis.millis
  val bittorrentClientId: String = conf.getString("bittorrent-client-id")
  val tcpConnectTtl: FiniteDuration = conf.getDuration("tcp-connect-ttl").toMillis.millis
  val targetNumPeers: Int = conf.getInt("target-num-peers")
  val cliRefreshRate: FiniteDuration = conf.getDuration("cli-refresh-rate").toMillis.millis

}

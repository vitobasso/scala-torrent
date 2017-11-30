package com.dominikgruber.scalatorrent.dht

import java.net.InetAddress

import cats.implicits.catsSyntaxMonadCombineSeparate
import cats.instances.either._
import cats.instances.list._
import com.dominikgruber.scalatorrent.dht.message.DhtMessage.{Address, Ip, Port}
import org.slf4j.{Logger, LoggerFactory}

object BootstrapNodes {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  private val names: List[String] = List(
    "router.bittorrent.com",
    "router.utorrent.com",
    "router.bitcomet.com",
    "dht.transmissionbt.com",
    "dht.aelitis.com"
  )

  private val port: Port = Port(6881)

  lazy val addresses: List[Address] = {
    val (errors, ips) = names.map(resolveIp).separate
    errors.foreach(log.warn)
    ips.map(Address(_, port))
  }

  private def resolveIp(hostName: String): Either[String, Ip] =
    try {
      val resolved = InetAddress.getByName(hostName)
      Ip.parse(resolved.getHostAddress)
    } catch {
      case e: Exception => Left(s"Failed to resolve host '$hostName': ${e.getMessage}" )
    }

}

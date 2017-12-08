package com.dominikgruber.scalatorrent.dht

import java.net.InetAddress

import cats.implicits.catsSyntaxMonadCombineSeparate
import cats.instances.either._
import cats.instances.list._
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import com.dominikgruber.scalatorrent.dht.message.ShortDescription.show
import org.slf4j.{Logger, LoggerFactory}

/**
  * Source: https://stackoverflow.com/questions/9451424/where-can-i-find-a-list-of-bittorent-dht-bootstrap-nodes
  */
object Bootstrap {

  private val log: Logger = LoggerFactory.getLogger(getClass)

  private val names: List[String] = List(
    "router.bittorrent.com",
    "router.utorrent.com",
//    "router.bitcomet.com",
    "dht.transmissionbt.com",
    "dht.aelitis.com"
  )

  private val port: Port = Port(6881)

  lazy val addresses: List[Address] = {
    val (errors, nodes) = names.map(createNode).separate
    errors.foreach(log.warn)
    log.info(s"Using bootstrap nodes: ${nodes.map(show(_)).mkString(", ")}")
    nodes
  }

  private def createNode(hostName: String): Either[String, Address] =
    for {
      ip <- resolveIp(hostName).right
    } yield Address(ip, port)

  private def resolveIp(hostName: String): Either[String, Ip] =
    try {
      val resolved = InetAddress.getByName(hostName)
      Ip.parse(resolved.getHostAddress)
    } catch {
      case e: Exception => Left(s"Failed to resolve host '$hostName': ${e.getMessage}" )
    }

}

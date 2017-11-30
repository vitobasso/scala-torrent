package com.dominikgruber.scalatorrent.dht

import java.net.InetAddress

import cats.implicits.catsSyntaxMonadCombineSeparate
import cats.instances.either._
import cats.instances.list._
import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
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

  lazy val nodes: List[NodeInfo] = {
    val (errors, nodes) = names.map(createNode).separate
    errors.foreach(log.warn)
    nodes
  }

  private def createNode(hostName: String): Either[String, NodeInfo] =
    for {
      ip <- resolveIp(hostName).right
      id <- literalNodeId(hostName).right
    } yield NodeInfo(id, Address(ip, port))

  /**
    * A collision should be as unlikely as when we randomly generate our own NodeId
    */
  private def literalNodeId(hostName: String): Either[String, NodeId] = {
    val str20: String = hostName.take(20).padTo(20, ' ')
    NodeId.validate(str20)
  }

  private def resolveIp(hostName: String): Either[String, Ip] =
    try {
      val resolved = InetAddress.getByName(hostName)
      Ip.parse(resolved.getHostAddress)
    } catch {
      case e: Exception => Left(s"Failed to resolve host '$hostName': ${e.getMessage}" )
    }

}

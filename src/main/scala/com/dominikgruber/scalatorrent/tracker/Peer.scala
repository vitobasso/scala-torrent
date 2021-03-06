package com.dominikgruber.scalatorrent.tracker

import java.net.{InetAddress, InetSocketAddress}
import com.dominikgruber.scalatorrent.dht.message.DhtMessage
import java.nio.charset.StandardCharsets.ISO_8859_1

import scala.language.implicitConversions

object PeerAddress {
  implicit def fromInetAddress(inetAddress: InetSocketAddress) =
    PeerAddress(inetAddress.getHostName, inetAddress.getPort)
  implicit def toInetAddress(address: PeerAddress): InetSocketAddress =
    new InetSocketAddress(address.ip, address.port)
  implicit def fromDhtAddress(address: DhtMessage.Address) =
    PeerAddress(address.ip.toString, address.port.toInt)
}
case class PeerAddress(ip: String, port: Int){
  override def toString: String = s"$ip:$port"
}

/**
 * Descriptions taken from the specification:
 * https://wiki.theory.org/BitTorrentSpecification#Metainfo_File_Structure
 */
case class Peer
(
 /**
  * Peer's self-selected ID
  */
  peerId: Option[String],

  /**
   * Peer's IP address either IPv6 (hexed) or IPv4 (dotted quad) or DNS name (string)
   */
  ip: String,

  /**
   * Peer's port number
   */
  port: Int

) {
  val address = PeerAddress(ip, port)
}

object Peer {

  def apply(peer: Map[String,Any]): Peer =
    Peer(
      peerId =
        if (peer.contains("peer id")) Some(peer("peer id").asInstanceOf[String])
        else None,
      ip = peer("ip").asInstanceOf[String],
      port = peer("port").asInstanceOf[Long].toInt
    )

  def createList(peers: Any): List[Peer] = peers match {
    case s: String => createList(s)
    case l: List[_] => createList(l.asInstanceOf[List[Map[String, Any]]])
    case _ => Nil
  }

  /**
   * Instead of using the dictionary model, the peers value may be a string
   * consisting of multiples of 6 bytes. First 4 bytes are the IP address and
   * last 2 bytes are the port number. All in network (big endian) notation.
   */
  def createList(peers: String): List[Peer] = {
    val (peerList, _) = peers.getBytes(ISO_8859_1).foldLeft((List[Peer](), Array[Byte]()))((z, byte) => {
      if (z._2.length < 5) (z._1, z._2 :+ byte)
      else {
        val ip = InetAddress.getByAddress(z._2.take(4))
        val port = BigInt(1, z._2.drop(4) :+ byte)
        val peer = Peer(None, ip.toString.drop(1), port.toInt)
        (peer :: z._1, Array[Byte]()) // This reverses the peer order but avoids appending to the List
      }
    })
    peerList
  }

  /**
   * The value is a list of dictionaries.
   */
  def createList(peers: List[Map[String,Any]]): List[Peer] =
    peers.map(p => apply(p))
}
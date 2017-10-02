package com.dominikgruber.scalatorrent.dht

import java.nio.ByteBuffer

import com.dominikgruber.scalatorrent.util.ByteUtil
import com.dominikgruber.scalatorrent.util.ByteUtil.unsignedByte
import shapeless.{Nat, Sized}

/**
  * http://www.bittorrent.org/beps/bep_0005.html
  */
object DhtMessage {

  type String20 = Sized[String, Nat._20]
  type String2 = Sized[String, Nat._2]

  /**
    * 20-byte sha-1 hash of an ip (or random chars?) identifying a node.
    */
  case class NodeId(value: String20) { //TODO rm boilerplate
    override def equals(other: scala.Any): Boolean = other match {
      case NodeId(otherValue) => value.unsized == otherValue.unsized
    }
    override def hashCode(): Int = value.unsized.hashCode
  }
  object NodeId {
    def validate(value: String): Either[String, NodeId] = {
      if(value.length == 20) Right(NodeId(Sized.wrap(value)))
      else Left(s"NodeId must have length 20, but was ${value.length}.")
    }
  }

  /**
    * 20-byte info hash identifying a torrent.
    * Generated by SHA-1 hash on a part of the torrent file (bencode format) describing the torrent's content.
    */
  case class InfoHash(value: String20) { //TODO rm boilerplate
    override def equals(other: scala.Any): Boolean = other match {
      case InfoHash(otherValue) => value.unsized == otherValue.unsized
    }
    override def hashCode(): Int = value.unsized.hashCode
  }
  object InfoHash {
    def validate(value: String): Either[String, InfoHash] = {
      if(value.length == 20) Right(InfoHash(Sized.wrap(value)))
      else Left(s"InfoHash must have length 20, but was ${value.length}.")
    }
  }

  /**
    * Random string identifying a query and response, usually 2-bytes.
    * Generated by the querying node.
    */
  case class TransactionId(value: String)

  case class Ip(value: Int) extends AnyVal {
    override def toString: String =
      ByteBuffer.allocate(4).putInt(value).array()
        .map(unsignedByte)
        .mkString(".")
  }
  object Ip {
    def apply(b1: Byte, b2: Byte, b3: Byte, b4: Byte): Ip = {
      val int: Int = (b1 << 24) | (b2 << 16) | (b3 << 8) | b4
      Ip(int)
    }
    def parse(str: String): Either[String, Ip] =
      str.split("\\.").map(_.toByte) match {
        case bytes: Array[Byte] if bytes.length == 4 =>
          Right(Ip(bytes(0), bytes(1), bytes(2), bytes(3)))
        case _ => Left(s"Failed to parse ip: '$str'")
      }
  }

  case class Port(value: Short) extends AnyVal {
    def toInt: Int = ByteUtil.unsignedShort(value)
  }
  object Port {
    def parse(value: Long): Either[String, Port] =
      if(value.isValidShort) Right(Port(value.toShort))
      else Left(s"Not a valid port: '$value'")
  }

  /**
    * 6-byte compact peer info
    *   - 4-byte ip address
    *   - 2-byte port
    */
  case class PeerInfo(ip: Ip, port: Port)

  /**
    * 26-byte compact node info
    *   - 20-byte Node ID
    *   - 4-byte ip address
    *   - 2-byte port
    */
  case class NodeInfo(node: NodeId, ip: Ip, port: Port)

  /**
    * Opaque token generated by a node responding to a get_peers query.
    * Needed send back an announce_peer.
    *
    * Reference implementation:
    *   SHA1 hash of the IP address concatenated onto a secret that changes every 5 minutes.
    *   Tokens up to 10 minutes old are accepted.
    */
  case class Token(value: String)

  sealed trait Message {
    val trans: TransactionId
  }
  sealed trait Query extends Message
  sealed trait Response extends Message
  case class Error(trans: TransactionId, code: Int, message: String) extends Message

  case class Ping(trans: TransactionId, origin: NodeId) extends Query
  case class Pong(trans: TransactionId, origin: NodeId) extends Response

  case class FindNode(trans: TransactionId, origin: NodeId, target: NodeId) extends Query
  case class FindNodeResponse(trans: TransactionId, origin: NodeId, nodes: Seq[NodeInfo]) extends Response

  case class GetPeers(trans: TransactionId, origin: NodeId, infoHash: InfoHash) extends Query
  case class PeersFound(trans: TransactionId, origin: NodeId, token: Token, peers: Seq[PeerInfo]) extends Response
  case class PeersNotFound(trans: TransactionId, origin: NodeId, token: Token, closestNodes: Seq[NodeInfo]) extends Response

  case class AnnouncePeer(trans: TransactionId, origin: NodeId, infoHash: InfoHash, port: Option[Port], token: Token) extends Query
  case class AnnouncePeerResponse(trans: TransactionId, origin: NodeId) extends Response

  def method(q: Query): String = q match {
    case _: Ping => "ping"
    case _: FindNode => "find_node"
    case _: GetPeers => "get_peers"
    case _: AnnouncePeer => "announce_peer"
  }
}

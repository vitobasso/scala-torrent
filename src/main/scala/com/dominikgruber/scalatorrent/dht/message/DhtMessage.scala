package com.dominikgruber.scalatorrent.dht.message

import java.net.{InetAddress, InetSocketAddress}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.ISO_8859_1

import com.dominikgruber.scalatorrent.util.ByteUtil
import com.dominikgruber.scalatorrent.util.ByteUtil.{Hex, unsignedByte}
import shapeless.Nat._
import shapeless.Sized
import shapeless.syntax.sized._
import cats.syntax.traverse._
import cats.instances.either._
import cats.instances.list._

/**
  * http://www.bittorrent.org/beps/bep_0005.html
  */
object DhtMessage {

  type String20 = Sized[String, _20]
  sealed trait Id20B {
    def value: String20
    def toBytes: Array[Byte] = value.unsized.getBytes(ISO_8859_1)
    def toBigInt: BigInt = BigInt(+1, toBytes)
    def distance(that: Id20B): BigInt = toBigInt ^ that.toBigInt
    override def hashCode(): Int = value.unsized.hashCode
    override def toString: String = s"NodeId(${Hex(toBytes)})"
  }
  sealed trait Id20BCompanion[A] {
    def apply(value: String20): A
    def validate(value: String): Either[String, A] =
      value.sized[_20]
        .toRight(s"Value must be 20 chars long, but was ${value.length}.")
        .right.map(apply)
    def random: A = {
      val str = ByteUtil.randomString(20)
      validate(str).right.get
    }
  }
  object Id20B {
    val Max = BigInt(1, Array.fill(20)(0xFF.toByte))
  }

  /**
    * 20-byte sha-1 hash of an ip (or random chars?) identifying a node.
    */
  case class NodeId(value: String20) extends Id20B {
    override def equals(other: scala.Any): Boolean = other match {
      case NodeId(otherValue) => value.unsized == otherValue.unsized
    }
  }
  object NodeId extends Id20BCompanion[NodeId]

  /**
    * 20-byte info hash identifying a torrent.
    * Generated by SHA-1 hash on a part of the torrent file (bencode format) describing the torrent's content.
    */
  case class InfoHash(value: String20) extends Id20B  {
    override def equals(other: scala.Any): Boolean = other match {
      case InfoHash(otherValue) => value.unsized == otherValue.unsized
    }
  }
  object InfoHash extends Id20BCompanion[InfoHash]

  /**
    * Random string identifying a query and response, usually 2-bytes.
    * Generated by the querying node.
    */
  case class TransactionId(value: String)
  object TransactionId {
    def random: TransactionId = TransactionId(ByteUtil.randomString(2))
  }

  case class Ip(value: Int) extends AnyVal {
    override def toString: String =
      ByteBuffer.allocate(4).putInt(value).array()
        .map(unsignedByte)
        .mkString(".")
  }
  object Ip {
    def apply(b1: Byte, b2: Byte, b3: Byte, b4: Byte): Ip = {
      val int: Int = (b1 << 24) |
          0x00FF0000 & (b2 << 16) |
          0x0000FF00 & (b3 << 8) |
          0x000000FF & b4
      Ip(int)
    }
    def parse(bytes: Array[Byte]): Either[String, Ip] = {
      if(bytes.length == 4) Right(Ip(bytes(0), bytes(1), bytes(2), bytes(3)))
      else Left(s"Invalid ip: '$bytes'")
    }
    def parse(addr: InetAddress): Either[String, Ip] = parse(addr.getAddress)
    def parse(str: String): Either[String, Ip] =
      str.split("\\.").toList.traverseU(toByte) match {
        case Right(bytes) => parse(bytes.toArray)
        case _ => Left(s"Invalid ip: '$str'")
      }
    private def toByte(str: String): Either[String, Byte] =
      try {
        val int = str.toInt
        if(isInUnsignedByteRange(int)) Right(int.toByte)
        else Left(s"Invalid ip: '$int' is outside the unsigned byte range")
      } catch {
        case e: NumberFormatException => Left(e.getMessage)
      }
    private def isInUnsignedByteRange(value: Int): Boolean =
      value >= 0 && value < Math.pow(2,8)
  }

  case class Port(value: Short) extends AnyVal {
    def toInt: Int = ByteUtil.unsignedShort(value)
  }
  object Port {
    def parse(value: Long): Either[String, Port] =
      if(isInUnsignedShortRange(value)) Right(Port(value.toShort))
      else Left(s"Invalid port: '$value' is outside the unsigned short range")
    private def isInUnsignedShortRange(value: Long): Boolean =
      value >= 0 && value < Math.pow(2,16)
  }

  case class Address(ip: Ip, port: Port) {
    def asJava: InetSocketAddress = new InetSocketAddress(ip.toString, port.toInt)
  }
  object Address {
    def parse(inetAddress: InetSocketAddress): Either[String, Address] =
      for {
        ip <- Ip.parse(inetAddress.getAddress).right
        port <- Port.parse(inetAddress.getPort).right
      } yield Address(ip, port)
  }

  /**
    * 6-byte compact peer info
    *   - 4-byte ip address
    *   - 2-byte port
    */
  type PeerInfo = Address

  /**
    * 26-byte compact node info
    *   - 20-byte Node ID
    *   - 4-byte ip address
    *   - 2-byte port
    */
  case class NodeInfo(id: NodeId, address: Address)
  object NodeInfo {
    def parse(id: NodeId, inetAddress: InetSocketAddress): Either[String, NodeInfo] =
      for {
        address <- Address.parse(inetAddress).right
      } yield NodeInfo(id, address)
  }

  /**
    * Opaque token generated by a node responding to a get_peers query.
    * Needed send back an announce_peer.
    *
    * Reference implementation:
    *   SHA1 hash of the IP address concatenated onto a secret that changes every 5 minutes.
    *   Tokens up to 10 minutes old are accepted.
    */
  case class Token(value: String)
  object Token {
    def forIp(ip: String): Token = Token(ip) //TODO hash with time + secret
  }

  sealed trait Message {
    val trans: TransactionId
  }
  sealed trait Query extends Message {
    val origin: NodeId
  }
  sealed trait Response extends Message {
    val origin: NodeId
  }
  case class Error(trans: TransactionId, code: Int, message: String) extends Message

  case class Ping(trans: TransactionId, origin: NodeId) extends Query
  case class Pong(trans: TransactionId, origin: NodeId) extends Response

  case class FindNode(trans: TransactionId, origin: NodeId, target: NodeId) extends Query
  case class NodesFound(trans: TransactionId, origin: NodeId, nodes: Seq[NodeInfo]) extends Response

  case class GetPeers(trans: TransactionId, origin: NodeId, infoHash: InfoHash) extends Query
  case class PeersFound(trans: TransactionId, origin: NodeId, token: Token, peers: Seq[PeerInfo]) extends Response
  case class PeersFoundAndNodes(trans: TransactionId, origin: NodeId, token: Token, peers: Seq[PeerInfo], closestNodes: Seq[NodeInfo]) extends Response
  case class PeersNotFound(trans: TransactionId, origin: NodeId, token: Token, closestNodes: Seq[NodeInfo]) extends Response

  case class AnnouncePeer(trans: TransactionId, origin: NodeId, infoHash: InfoHash, port: Option[Port], token: Token) extends Query
  case class PeerReceived(trans: TransactionId, origin: NodeId) extends Response

  def method(q: Query): String = q match {
    case _: Ping => "ping"
    case _: FindNode => "find_node"
    case _: GetPeers => "get_peers"
    case _: AnnouncePeer => "announce_peer"
  }
}

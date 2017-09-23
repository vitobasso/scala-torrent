package com.dominikgruber.scalatorrent.tracker.udp

import java.nio.ByteBuffer

import com.dominikgruber.scalatorrent.tracker
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.util.ByteUtil._
import com.dominikgruber.scalatorrent.util.SBinaryFormats._
import sbinary.{Format, Reads}
import sbinary.DefaultProtocol._ // implicit Formats needed for to/fromBytes
import shapeless._

import scala.util.Try

object UdpEncoding {

  implicit val formatInfoHash: Format[InfoHash] = formatAsBytes(20)(_.value, b => InfoHash(Sized.wrap(b)))
  implicit val formatPeerId: Format[PeerId] = formatAsBytes(20)(_.value, b => PeerId(Sized.wrap(b)))
  implicit val readsPeerAddr: Reads[PeerAddress] = readsVia(PeerAddress.fromParts _)
  implicit val readsListPeerAddr: Reads[List[PeerAddress]] = readsToEnd[PeerAddress]

  def encode(req: Request): Array[Byte] = req match {
    case c: ConnectRequest => toBytesVia(Connect.requestToParts)(c)
    case a: AnnounceRequest => toBytesVia(Announce.requestToParts)(a)
  }

  def decode(bytes: Array[Byte]): Try[Response] = {
    Try { fromBytesVia(Connect.responseFromParts _)(bytes) } orElse
      Try { fromBytesVia(Announce.responseFromParts _)(bytes) }
  }

  object Connect {
    val action = 0
    def requestToParts(req: ConnectRequest) =
      (0x0000041727101980L, action, req.trans.value)
    def responseFromParts(action: Int, trans: Int, conn: Long): ConnectResponse = {
      require(action == Connect.action)
      ConnectResponse(TransactionId(trans), ConnectionId(conn))
    }
  }

  object Announce {
    val action = 1
    def requestToParts(req: AnnounceRequest) =
      (req.conn.value, action, req.trans.value, req.torrentHash, req.peerId,
        req.downloaded, req.left, req.uploaded, req.event.code, req.ip.toInt,
        req.key.toInt, req.numWant, req.port.toShort)
    def responseFromParts(action: Int, trans: Int, interval: Int, leechers: Int, seeders: Int,
                          peers: List[PeerAddress]): AnnounceResponse = {
      require(action == Announce.action)
      AnnounceResponse(TransactionId(trans), interval, leechers, seeders, peers)
    }
  }

  object PeerAddress {
    def fromParts(host: Int, port: Short): PeerAddress =
      tracker.PeerAddress(decodeIp(host), unsignedShort(port))
    private def decodeIp(ip: Int): String =
      ByteBuffer.allocate(4).putInt(ip).array()
        .map(unsignedByte)
        .mkString(".")
  }

  object Error {
    val action = 3
  }

}
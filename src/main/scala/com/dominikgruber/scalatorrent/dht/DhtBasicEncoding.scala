package com.dominikgruber.scalatorrent.dht


import java.nio.charset.StandardCharsets.ISO_8859_1

import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import com.dominikgruber.scalatorrent.dht.DhtMessage._
import com.dominikgruber.scalatorrent.util.SBinaryFormats._
import sbinary.{Reads, Writes}
import sbinary.Operations.{fromByteArray, toByteArray}
import sbinary.DefaultProtocol._ // implicit Formats needed for to/fromBytes

object DhtBasicEncoding {

  implicit val readsPeerAddr: Reads[PeerInfo] = readsVia(PeerInfo.fromParts _)
  implicit val writesPeerAddr: Writes[PeerInfo] = writesVia(PeerInfo.toParts)

  def parsePeerInfos(str: String): Seq[PeerInfo] =
    str.grouped(6).map(parsePeerInfo).toSeq

  //TODO validation?
  def parsePeerInfo(str: String): PeerInfo =
    fromByteArray[PeerInfo](str.getBytes(ISO_8859_1))

  def serializePeerInfo(peerInfo: PeerInfo): String =
    new String(toByteArray(peerInfo), ISO_8859_1)

  def parseNodeInfos(str: String): Either[String, Seq[NodeInfo]] =
    str.grouped(26).toList.traverseU(parseNodeInfo)

  def parseNodeInfo(str: String): Either[String, NodeInfo] = {
    val addrBytes = str.substring(20).getBytes(ISO_8859_1)
    val addr = fromBytesVia(PeerInfo.fromParts _)(addrBytes)
    for {
      id <- NodeId.validate(str.substring(0,20)).right
    } yield NodeInfo(id, addr.ip, addr.port)
  }

  def serializeNodeInfo(nodeInfo: NodeInfo): String = {
    val peerInfo = DhtMessage.PeerInfo(nodeInfo.ip, nodeInfo.port)
    val addrByes: Array[Byte] = toBytesVia(PeerInfo.toParts)(peerInfo)
    val addrStr = new String(addrByes, ISO_8859_1)
    val nodeId: String = nodeInfo.node.value.unsized
    nodeId + addrStr
  }

  object PeerInfo { //TODO duplicate in UdpEncoding
    def fromParts(host: Int, port: Short): PeerInfo =
      DhtMessage.PeerInfo(Ip(host), Port(port))
    def toParts(peerInfo: PeerInfo): (Int, Short) =
      (peerInfo.ip.value, peerInfo.port.value)
  }

}
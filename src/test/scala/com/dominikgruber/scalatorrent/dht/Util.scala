package com.dominikgruber.scalatorrent.dht

import java.nio.charset.StandardCharsets.ISO_8859_1

import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import com.dominikgruber.scalatorrent.util.ByteUtil
import com.dominikgruber.scalatorrent.util.ByteUtil.{Bytes, bytes}

object Util {

  /**
    * Quick node id with the most significant bit defined followed with zeroes
    */
  def node(hexByte: String): NodeId = {
    require(hexByte.length == 2)
    val b: Bytes = bytes(hexByte) ++ Array.fill(19)(0.toByte)
    val str: String = new String(b, ISO_8859_1)
    NodeId.validate(str).right.get
  }

  /**
    * Quick [[InfoHash]] with the most significant bit defined followed with zeroes
    */
  def hash(hexByte: String): InfoHash =
    InfoHash(node(hexByte).value)


  def nodeInfo(hexByte: String): NodeInfo = {
    require(hexByte.length == 2)
    val b = ByteUtil.bytes(hexByte).head
    val address = Address(Ip(0, 0, 0, b), Port(b))
    NodeInfo(node(hexByte), address)
  }

  def peerInfo(hexByte: String): PeerInfo = {
    require(hexByte.length == 2)
    val b = ByteUtil.bytes(hexByte).head
    Address(Ip(0, 0, 0, b), Port(b))
  }

}

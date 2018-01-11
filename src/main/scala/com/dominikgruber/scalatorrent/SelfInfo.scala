package com.dominikgruber.scalatorrent

import com.dominikgruber.scalatorrent.dht.message.DhtMessage.NodeId

import scala.util.Random

case class SelfInfo(clientId: String) {

  /**
    * Protocol string. Sent in BitTorrent handshakes to identify the protocol.
    */
  val pstr = "BitTorrent protocol"

  /**
    * Sent in BitTorrent handshakes.
    */
  val extension = Vector[Byte](0, 0, 0, 0, 0, 0, 0, 1)

  /**
    * 20-byte string used as a unique ID for the client.
    * Azureus-style: '-', two characters for client id, four ascii digits for
    * version number, '-', followed by random numbers.
    * 'SC' was chosen for the client id since 'ST' was already taken.
    *
    * @todo Generate once and persist
    */
  lazy val peerId: String = {
    def rand = 100000 + Random.nextInt(899999)
    s"-$clientId-$rand$rand"
  }

  //TODO generate once and persist
  val nodeId: NodeId = NodeId.random

}

package com.dominikgruber.scalatorrent

import scala.util.Random

object SelfInfo {

  val pstr = "BitTorrent protocol"
  val extension = Vector[Byte](0, 0, 0, 0, 0, 0, 0, 0)

  /**
    * 20-byte string used as a unique ID for the client.
    * Azureus-style: '-', two characters for client id, four ascii digits for
    * version number, '-', followed by random numbers.
    * 'SC' was chosen for the client id since 'ST' was already taken.
    *
    * @todo Generate once and persist
    */
  lazy val selfPeerId: String = {
    def rand = 100000 + Random.nextInt(899999)
    s"-SC0001-$rand$rand"
  }

}

package com.dominikgruber.scalatorrent.peerwireprotocol

import java.nio.charset.StandardCharsets.ISO_8859_1

import com.dominikgruber.scalatorrent.actor.Hex

/**
 * The handshake is a required message and must be the first message transmitted
 * by the client. It is (49+len(pstr)) bytes long.
 *
 * handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
 *
 * - pstrlen: string length of <pstr>, as a single raw byte
 * - pstr: string identifier of the protocol
 * - reserved: eight (8) reserved bytes. All current implementations use all
 * zeroes. Each bit in these bytes can be used to change the behavior of the
 * protocol. An email from Bram suggests that trailing bits should be used first,
 * so that leading bits may be used to change the meaning of trailing bits.
 * - info_hash: 20-byte SHA1 hash of the info key in the metainfo file. This is
 * the same info_hash that is transmitted in tracker requests.
 * - peer_id: 20-byte string used as a unique ID for the client. This is usually
 * the same peer_id that is transmitted in tracker requests (but not always e.g.
 * an anonymity option in Azureus).
 *
 * In version 1.0 of the BitTorrent protocol, pstrlen = 19, and pstr =
 * "BitTorrent protocol".
 */
case class Handshake(pstr: String, extension: Vector[Byte], peerId: String, infoHash: Vector[Byte]) {

  def marshal: Vector[Byte] =
    Vector.concat(
      Vector[Byte](pstr.length.toByte),
      pstr.getBytes(ISO_8859_1),
      extension,
      infoHash,
      peerId.getBytes(ISO_8859_1)
    )

  /**
   * Hex string representation of the SHA1 value
   */
  lazy val infoHashString: String = infoHash.map("%02X" format _).mkString

  override def toString: String = s"Handshake($pstr, ${Hex(extension)}, $peerId, $infoHashString)"
}

object Handshake {

  //TODO make cleaner, use parser. https://github.com/harrah/sbinary
  def unmarshall(message: Vector[Byte]): Option[Handshake] = {
    val pstrlen = message(0)
    if(message.length >= pstrlen + 49) {
      val pstrEnd: Int = 1 + pstrlen
      val pstr: String = new String(message.slice(1, pstrEnd).toArray, ISO_8859_1)
      val extensionEnd: Int = pstrEnd + 8
      val extension: Vector[Byte] = message.slice(pstrEnd, extensionEnd)
      val infoHashEnd: Int = extensionEnd + 20
      val infoHash: Vector[Byte] = message.slice(extensionEnd, infoHashEnd)
      val peerIdEnd: Int = infoHashEnd + 20
      val peerId: String = new String(message.slice(infoHashEnd, peerIdEnd).toArray, ISO_8859_1)
      Some(Handshake(pstr, extension, peerId, infoHash))
    } else None
  }

}

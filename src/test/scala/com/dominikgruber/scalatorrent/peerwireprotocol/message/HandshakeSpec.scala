package com.dominikgruber.scalatorrent.peerwireprotocol.message

import com.dominikgruber.scalatorrent.SelfInfo
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.util.UnitSpec

class HandshakeSpec extends UnitSpec {

  val selfInfo = SelfInfo("SC0001")
  val peerId = "-SC0001-012345678901"
  val exampleMarshaledHandshake = Vector[Byte](19, 66, 105, 116, 84, 111, 114, 114, 101, 110, 116, 32, 112, 114, 111, 116, 111, 99, 111, 108, 0, 0, 0, 0, 0, 0, 0, 1, 54, 63, -103, -95, 72, -64, -23, -78, -46, -103, -22, -114, 84, -116, -3, -15, -126, -122, -24, 88, 45, 83, 67, 48, 48, 48, 49, 45, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 48, 49)

  "marshal" should "work with the example torrent" in {
    val handshake = Handshake(selfInfo.pstr, selfInfo.extension, peerId, exampleMetainfo.fileInfo.infoHash)
    handshake.marshal should be (exampleMarshaledHandshake)
  }

  "unmarshal" should "work with the example torrent" in {
    Handshake.unmarshal(exampleMarshaledHandshake) should be (
      Some(Handshake(selfInfo.pstr, selfInfo.extension, peerId, exampleMetainfo.fileInfo.infoHash)))
  }

  def exampleMetainfo: MetaInfo = {
    val sourceString = loadTorrentFile("/metainfo/ubuntu-12.04.4-server-amd64.iso.torrent")
    MetaInfo(sourceString)
  }
}
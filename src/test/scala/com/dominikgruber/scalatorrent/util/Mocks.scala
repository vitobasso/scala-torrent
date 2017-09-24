package com.dominikgruber.scalatorrent.util

import com.dominikgruber.scalatorrent.Torrent.BlockSize
import com.dominikgruber.scalatorrent.metainfo.{MetaInfo, SingleFileMetaInfo}
import com.dominikgruber.scalatorrent.tracker.Peer
import com.dominikgruber.scalatorrent.tracker.http.TrackerResponseWithSuccess

object Mocks {

  def fileMetaInfo(totalLength: Int = 0, pieceLength: Int = 1, file: String = "test-file"): SingleFileMetaInfo =
    SingleFileMetaInfo(infoHash, pieceLength, "", None, file, totalLength, None)

  def metaInfo(totalLength: Int = 0, pieceLength: Int = 1): MetaInfo =
    MetaInfo(fileMetaInfo(totalLength, pieceLength), "", None, None, None, None, None)

  val infoHash: Vector[Byte] = Vector.fill(20)(0.toByte)

  def trackerResponse(peers: List[Peer] = List(peer)) =
    TrackerResponseWithSuccess(0, None, None, 0, 0, peers, None)

  def block(value: Byte = 0.toByte): Vector[Byte] = {
    Vector.fill(BlockSize)(value)
  }

  val peer = Peer(None, "peer-ip", 0)

}

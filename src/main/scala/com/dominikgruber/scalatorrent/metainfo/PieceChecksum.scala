package com.dominikgruber.scalatorrent.metainfo

import java.nio.charset.StandardCharsets.ISO_8859_1

import com.dominikgruber.scalatorrent.metainfo.PieceChecksum.HashSize
import com.dominikgruber.scalatorrent.util.ByteUtil.{Bytes, sha1Hash}
import org.slf4j.LoggerFactory

case class PieceChecksum(meta: MetaInfo) {

  val allPiecesHash: String = meta.fileInfo.pieces

  def apply(index: Int, bytes: Bytes): Boolean = {
    val pieceHash: String =
      allPiecesHash.drop(HashSize*index).take(HashSize)
    sha1Hash(bytes) sameElements pieceHash.getBytes(ISO_8859_1)
  }

  val log = LoggerFactory.getLogger(getClass)
}

object PieceChecksum {

  /**
    * Size of the hash per piece
    */
  val HashSize: Int = 20
}
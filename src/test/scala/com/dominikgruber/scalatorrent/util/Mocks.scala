package com.dominikgruber.scalatorrent.util

import com.dominikgruber.scalatorrent.metainfo.{MetaInfo, SingleFileMetaInfo}

object Mocks {

  def fileMetaInfo(totalLength: Int = 0, pieceLength: Int = 1, file: String = "test-file"): SingleFileMetaInfo =
    SingleFileMetaInfo(infoHash, pieceLength, "", None, file, totalLength, None)

  def metaInfo(totalLength: Int = 0, pieceLength: Int = 1): MetaInfo =
    MetaInfo(fileMetaInfo(totalLength, pieceLength), "", None, None, None, None, None)

  val infoHash: Vector[Byte] = Vector.fill(20)(0.toByte)

}

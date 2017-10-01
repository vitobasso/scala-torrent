package com.dominikgruber.scalatorrent.metainfo

/**
 * Descriptions taken from the specification:
 * https://wiki.theory.org/BitTorrentSpecification#Metainfo_File_Structure
 */
case class FileInfo
(
  /**
   * Length of the file in bytes.
   */
  length: Long,

  /**
   * A 32-character hexadecimal string corresponding to the MD5 sum of the file.
   * This is not used by BitTorrent at all, but it is included by some programs
   * for greater compatibility.
   */
  md5sum: Option[String],

  /**
   * A list containing one or more string elements that together represent the
   * path and filename. Each element in the list corresponds to either a
   * directory name or (in the case of the final element) the filename.
   */
  path: List[String]

) {

  /**
   * Convert the content to a map conforming to the .torrent file standard
   */
  def toMap: Map[String,Any] = {
    val map = Map("length" -> length, "path" -> path)
    if (md5sum.isDefined) map + ("md5sum" -> md5sum.get)
    else map
  }
}

object FileInfo {

  def apply(files: List[Map[String,Any]]): List[FileInfo] =
    files.map(f => apply(f))

  def apply(file: Map[String,Any]): FileInfo =
    FileInfo(
      length = file("length").asInstanceOf[Long],
      md5sum =
        if (file.contains("md5sum")) Some(file("md5sum").asInstanceOf[String])
        else None,
      path = file("path").asInstanceOf[List[String]]
    )
}
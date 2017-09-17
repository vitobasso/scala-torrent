package com.dominikgruber.scalatorrent.metainfo

import java.io.File
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.Date

import com.dominikgruber.scalatorrent.bencode.{BencodeEncoder, BencodeParser}

import scala.collection.mutable
import scala.io.{Codec, Source}

/**
 * Descriptions taken from the specification:
 * https://wiki.theory.org/BitTorrentSpecification#Metainfo_File_Structure
 */
case class MetaInfo
(
  /**
   * A dictionary that describes the file(s) of the torrent. There are two
   * possible forms: one for the case of a 'single-file' torrent with no
   * directory structure, and one for the case of a 'multi-file' torrent.
   */
  fileInfo: FileMetaInfo,

  /**
   * The announce URL of the tracker
   */
  announce: String,

  /**
   * This is an extention to the official specification, offering
   * backwards-compatibility. (list of lists of strings).
    * http://bittorrent.org/beps/bep_0012.html
   */
  announceList: Option[List[List[String]]],

  /**
   * The creation time of the torrent.
   */
  creationDate: Option[Date],

  /**
   * Free-form textual comments of the author.
   */
  comment: Option[String],

  /**
   * Name and version of the program used to create the .torrent.
   */
  createdBy: Option[String],

  /**
   * The string encoding format used to generate the pieces part of the info
   * dictionary in the .torrent metafile
   */
  encoding: Option[String]

) {

  /**
   * Convert the content to a map conforming to the .torrent file standard
   */
  def toMap: Map[String,Any] = {
    val map: mutable.Map[String,Any] = mutable.Map(
      "info" -> fileInfo.toMap,
      "announce" -> announce
    )
    if (announceList.isDefined) map += ("announce-list" -> announceList.get)
    if (creationDate.isDefined) map += ("creation date" -> (creationDate.get.getTime / 1000l).toInt)
    if (comment.isDefined) map += ("comment" -> comment.get)
    if (createdBy.isDefined) map += ("created by" -> createdBy.get)
    if (encoding.isDefined) map += ("encoding" -> encoding.get)
    map.toMap
  }

  /**
   * Bencoded string conforming to the .torrent file standard
   */
  def bencodedString: Either[String, String] =
    BencodeEncoder(toMap)

  def hash: String = fileInfo.infoHashString
}

object MetaInfo {

  def apply(file: File): MetaInfo = {
    val source = Source.fromFile(file)(Codec.ISO8859)
    val info = source.mkString
    source.close()
    apply(info)
  }

  def apply(bencode: String): MetaInfo = {
    val info = BencodeParser(bencode).get.asInstanceOf[Map[String,Any]]
    val infoHash = calculateInfoHashFromBencodedString(bencode)
    apply(info, infoHash)
  }

  /**
   * This method assumes that the input string adheres to the bencode / .torrent
   * format which would make the info key the last one in the dictionary.
   *
   * Getting the string directly from the input is safer than piecing it back
   * together from the parsed input since the original input might contain
   * undocumented keys that are ignored by the parser.
   */
  def getInfoValueFromBencodedString(bencode: String): String = {
    val infoDict = bencode.substring(bencode.lastIndexOf("4:info") + 6, bencode.length - 1)
    if(infoDict.contains("8:url-list")) infoDict.substring(0, infoDict.lastIndexOf("8:url-list")) else infoDict //TODO better solution
  }

  def calculateInfoHashFromBencodedString(bencode: String): Vector[Byte] = {
    val infoValue = getInfoValueFromBencodedString(bencode)
    val md = java.security.MessageDigest.getInstance("SHA-1")
    md.digest(infoValue.getBytes(ISO_8859_1)).toVector
  }

  def apply(info: Map[String,Any], infoHash: Vector[Byte]): MetaInfo = {
    MetaInfo(
      fileInfo = FileMetaInfo(info("info").asInstanceOf[Map[String,Any]], infoHash),
      announce = info("announce").asInstanceOf[String],
      announceList =
        if (info.contains("announce-list")) Some(info("announce-list").asInstanceOf[List[List[String]]])
        else None,
      creationDate =
        if (info.contains("creation date")) Some(new Date(info("creation date").asInstanceOf[Long] * 1000l))
        else None,
      comment =
        if (info.contains("comment")) Some(info("comment").asInstanceOf[String])
        else None,
      createdBy =
        if (info.contains("created by")) Some(info("created by").asInstanceOf[String])
        else None,
      encoding =
        if (info.contains("encoding")) Some(info("encoding").asInstanceOf[String])
        else None
    )
  }
}
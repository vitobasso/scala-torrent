package com.dominikgruber.scalatorrent.actor

import java.nio.file.{Files, Path, Paths}

import akka.actor.{Actor, ActorLogging}
import com.dominikgruber.scalatorrent.actor.Storage._
import com.dominikgruber.scalatorrent.metainfo.FileMetaInfo

import scala.collection.BitSet
import scalax.io.{OverwriteAll, Resource}

object Storage {
  case class Load(index: Int)
  case class Store(index: Int, bytes: Vector[Byte])
  case class Loaded(index: Int, bytes: Vector[Byte])
  case object StatusPlease
  case class Status(piecesWeHave: BitSet)
}

class Storage(meta: FileMetaInfo) extends Actor with ActorLogging{

  val pieceLen: Int = meta.pieceLength
  val path: Path = Paths.get(meta.name)

  log.debug(s"Storing to file: ${meta.name}")
  private val storage = Resource.fromFile(meta.name)

  override def receive: Receive = {
    case Load(i) =>
      val bytes = storage.bytes.drop(i * pieceLen).take(pieceLen)
      sender ! Loaded(i, bytes.toVector)
    case Store(i, bytes) =>
      //TODO validate length & position
      storage.patch(i * pieceLen, bytes, OverwriteAll)
    case StatusPlease =>
      sender ! Status(piecesWeHave)
  }

  if(!Files.exists(path)) fillWithZeroes

  private def fillWithZeroes = {
    val last = meta.numPieces - 1
    for (i <- 0 until last) {
      val zeroes = Vector.fill(meta.pieceLength)(0.toByte)
      storage.patch(i * pieceLen, zeroes, OverwriteAll)
    }
    //last piece may be shorter
    val remaining = (meta.totalBytes % meta.pieceLength).toInt
    val zeroes = Vector.fill(remaining)(0.toByte)
    storage.patch(last * pieceLen, zeroes, OverwriteAll)
  }

  def piecesWeHave: BitSet = {
    val indexes = storage.bytes.sliding(pieceLen, pieceLen)
      .zipWithIndex
      .collect{ case (piece, i) if piece.exists(_ != 0.toByte) => i }
    BitSet(indexes.toSeq: _*)
  }

}
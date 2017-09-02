package com.dominikgruber.scalatorrent.actor

import java.nio.file.{Files, Path, Paths}

import akka.actor.{Actor, ActorLogging}
import com.dominikgruber.scalatorrent.actor.Storage._
import com.dominikgruber.scalatorrent.metainfo.FileMetaInfo

import scala.collection.BitSet
import scalax.io.{OverwriteAll, Resource}

object Storage {
  case class Load(index: Int)
  case class Store(index: Int, bytes: Array[Byte])
  case class Loaded(index: Int, bytes: Array[Byte])
  case object StatusPlease
  case class Status(piecesWeHave: BitSet)
}

class Storage(meta: FileMetaInfo) extends Actor with ActorLogging {

  val pieceLen: Int = meta.pieceLength
  val path: Path = Paths.get(meta.name)

  log.debug(s"Using file: ${meta.name}")
  private val storage = Resource.fromFile(meta.name)

  override def preStart(): Unit = {
    if(!Files.exists(path)) initWithZeroes
  }

  override def receive: Receive = {
    case Load(i) =>
      val bytes = storage.bytes.drop(i * pieceLen).take(pieceLen)
      sender ! Loaded(i, bytes.toArray)
    case Store(i, bytes) =>
      //TODO validate length & position
      storage.patch(i * pieceLen, bytes, OverwriteAll)
      log.info(s"Stored piece $i")
    case StatusPlease =>
      sender ! Status(piecesWeHave)
  }

  /**
    * For operations on the whole file.
    * We want to transfer big chunks to and from disk each time, but they still need to fit in memory.
    */
  val pageSize: Int = 10 * 1000 * 1000 //10Mb
  val totalSize: Long = meta.totalBytes
  lazy val numWholePages: Int = (totalSize/pageSize).toInt

  private def initWithZeroes: Unit = {
    log.info(s"Initializing file with $totalSize zeros")

    val zeros: Array[Byte] = Array.fill(pageSize)(0.toByte)
    for (_ <- 0 until numWholePages) {
      storage.append(zeros)
    }

    //last piece may be shorter
    val remaining: Int = (totalSize % pageSize).toInt
    val zerosRemaining: Array[Byte] = Array.fill(remaining)(0.toByte)
    storage.append(zerosRemaining)
  }

  def piecesWeHave: BitSet = {
    log.info(s"Recovering progress from file")
    val pageSize: Int = this.pageSize - this.pageSize % pieceLen //a multiple of pieceLen
    val piecesPerPage: Int = pageSize/pieceLen

    val input = storage.bytes
    val indexes = for {
      i <- 0 to numWholePages //include the last, non-whole, page
      page = input.drop(i * pageSize).take(pageSize) //load a big chunk each time for performance
      (piece, j) <- page.grouped(pieceLen).zipWithIndex if piece.exists(_ != 0.toByte)
      pieceIndex: Int = i * piecesPerPage + j
    } yield pieceIndex
    BitSet(indexes: _*)
  }

}

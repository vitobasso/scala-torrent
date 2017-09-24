package com.dominikgruber.scalatorrent

import java.nio.file.{Files, Path, Paths}

import akka.actor.{Actor, ActorLogging}
import com.dominikgruber.scalatorrent.Storage._
import com.dominikgruber.scalatorrent.metainfo.FileMetaInfo

import scala.collection.BitSet
import scala.pickling.Defaults._
import scala.pickling.json._
import scalax.io.{OverwriteAll, Resource}

object Storage {
  case class Load(index: Int)
  case class Store(index: Int, bytes: Array[Byte])
  case class Loaded(index: Int, bytes: Array[Byte])
  case object StatusPlease
  case class Status(piecesWeHave: BitSet)
  case object Complete
}

class Storage(meta: FileMetaInfo) extends Actor with ActorLogging {

  val totalSize: Long = meta.totalBytes
  val pieceLen: Int = meta.pieceLength
  val fileName = meta.name
  val path: Path = Paths.get(fileName)

  log.debug(s"Using file: $fileName")
  private val storage = Resource.fromFile(fileName)

  override def preStart(): Unit = {
    if(!Files.exists(path)) initWithZeroes
  }

  override def receive: Receive = {
    case Load(i) =>
      val bytes = storage.bytes
        .ltake(totalSize).toStream //exclude metadata at the end
        .drop(i * pieceLen).take(pieceLen)
      sender ! Loaded(i, bytes.toArray)
    case Store(i, bytes) =>
      //TODO validate length & position
      storage.patch(i * pieceLen, bytes, OverwriteAll)
      updateStatus(i)
      log.info(s"Stored piece $i")
    case StatusPlease =>
      loadStatus match {
        case Some(status) => sender ! Status(status)
        case None => sender ! Complete
      }
  }

  /**
    * For operations on the whole file.
    * We want to transfer big chunks to and from disk each time, but they still need to fit in memory.
    */
  val pageSize: Int = 10 * 1000 * 1000 //10Mb


  private def initWithZeroes: Unit = {
    val numWholePages: Int = (totalSize/pageSize).toInt
    log.info(s"Initializing file with $totalSize zeros")

    val zeros: Array[Byte] = Array.fill(pageSize)(0.toByte)
    for (_ <- 0 until numWholePages) {
      storage.append(zeros)
    }

    //last piece may be shorter
    val remaining: Int = (totalSize % pageSize).toInt
    val zerosRemaining: Array[Byte] = Array.fill(remaining)(0.toByte)
    storage.append(zerosRemaining)

    storeStatus(BitSet.empty)
  }

  private def loadStatus: Option[BitSet] = {
    val bytes = storage.bytes.ldrop(totalSize).toArray
    if(bytes.isEmpty)
      None // complete
    else {
      val statusStr: String = new String(bytes)
      val status = JSONPickle(statusStr).unpickle[BitSet]
      Some(status)
    }
  }

  private def updateStatus(newPiece: Int): Unit = {
    loadStatus.foreach { oldStatus =>
      storage.truncate(totalSize)
      val newStatus = oldStatus + newPiece
      if(newStatus.size < meta.numPieces)
        storeStatus(newStatus)
    }
  }

  private def storeStatus(status: BitSet): Unit = {
    val statusStr: String = status.pickle.value
    storage.append(statusStr)
  }

}

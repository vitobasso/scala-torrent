package com.dominikgruber.scalatorrent.storage

import akka.actor.{Actor, ActorLogging, Props}
import com.dominikgruber.scalatorrent.metainfo.FileMetaInfo
import com.dominikgruber.scalatorrent.storage.Storage._

import scala.collection.BitSet

class Storage(meta: FileMetaInfo) extends Actor with ActorLogging {

  val files: FileManager = {
    val maxChunk: Int = 10 * 1000 * 1000 //10Mb
    FileManager(meta, maxChunk)
  }
  val status = StoredStatus(meta.infoHashString, meta.numPieces)

  override def preStart(): Unit =
    if(files.initIfNew()) {
      log.info(s"Initializing files")
      status.store(BitSet.empty)
    }

  override def receive: Receive = {
    case Load(i) =>
      val bytes = files.loadPiece(i)
      sender ! Loaded(i, bytes)
    case Store(i, bytes) =>
      files.storePiece(i, bytes)
      status.update(i)
      log.info(s"Stored piece $i")
    case StatusPlease =>
      status.load match {
        case Some(status) => sender ! Status(status)
        case None => sender ! Complete
      }
  }

}

object Storage {
  def props(meta: FileMetaInfo) = Props(classOf[Storage], meta)

  case class Load(index: Int)
  case class Store(index: Int, bytes: Array[Byte])
  case class Loaded(index: Int, bytes: Array[Byte])
  case object StatusPlease
  case class Status(piecesWeHave: BitSet)
  case object Complete
}

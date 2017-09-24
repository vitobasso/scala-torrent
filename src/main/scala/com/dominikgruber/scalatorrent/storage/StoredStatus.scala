package com.dominikgruber.scalatorrent.storage

import scala.collection.BitSet
import scala.pickling.Defaults._
import scala.pickling.json.{JSONPickle, _}
import scalax.file.Path
import scalax.io.Resource

case class StoredStatus(hash: String, totalPieces: Int) {

  val path = Path(hash) / s"$hash.status"
  protected lazy val storage = Resource.fromFile(path.jfile)

  def load: Option[BitSet] = {
    val bytes = storage.bytes.toArray
    if(bytes.isEmpty)
      None // complete
    else {
      val statusStr = new String(bytes)
      val status = JSONPickle(statusStr).unpickle[BitSet]
      Some(status)
    }
  }

  def update(newPiece: Int): Unit = {
    load.foreach { oldStatus =>
      clear
      val newStatus = oldStatus + newPiece
      if(newStatus.size < totalPieces)
        store(newStatus)
      else
        path.delete()
    }
  }

  def store(status: BitSet): Unit = {
    val statusStr: String = status.pickle.value
    storage.append(statusStr)
  }

  def clear: Unit = storage.truncate(0)

}

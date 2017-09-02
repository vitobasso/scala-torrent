package com.dominikgruber.scalatorrent.transfer

import com.dominikgruber.scalatorrent.actor.Torrent.BlockSize
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.Request
import com.dominikgruber.scalatorrent.transfer.PickRandom._

import scala.collection.{BitSet, mutable}

/**
  * Keeps track of which pieces are done, missing or currently being downloaded.
  * Keeps data in memory for the pieces currently in progress.
  *
  * Picks a random missing block to be downloaded next.
  *   - prefers completing a piece in progress over starting a new one
  */
case class TransferStatus(metaInfo: MetaInfo) {

  val totalPieces: Int = metaInfo.fileInfo.numPieces
  val blocksPerPiece: Int = metaInfo.fileInfo.pieceLength / BlockSize

  type Flags = mutable.Seq[Boolean]
  type Bytes = Array[Byte]
  type Blocks = mutable.Map[Int, Bytes]

  /**
    * Marks which pieces from 0 to [[totalPieces]] we have completed
    */
  val pieceStatus: Flags =
    mutable.Seq.fill(totalPieces)(false)

  /**
    * Stores blocks from each piece being currently downloaded
    */
  private val piecesInProgress: mutable.Map[Int, Blocks] =
    mutable.Map.empty

  def addBlock(piece: Int, block: Int, data: Bytes): Option[Bytes] =
    getBlocksIfPieceIncomplete(piece)
      .flatMap { blocks =>
        blocks(block) = data
        if(blocks.size == blocksPerPiece)
          completePiece(piece)
        else None
      }

  def completePiece(piece: Int): Option[Bytes] = {
    pieceStatus(piece) = true
    piecesInProgress.remove(piece)
      .map(aggregateBlocks)
  }

  private def aggregateBlocks(blocks: Blocks): Bytes =
    blocks.toArray.sortBy(_._1).flatMap(_._2)

  /**
    * @param available in the remote peer
    * @return whether they have any block that we're missing
    */
  def isAnyPieceNew(available: BitSet): Boolean =
    newPieces(available).nonEmpty

  private def newPieces(available: BitSet) = {
    val alreadyHave = BitSetUtil.fromBooleans(pieceStatus)
    available &~ alreadyHave
  }

  /**
    * @param available in the remote peer
    * @return A [[Request]] to get a piece we're missing from a remote peer
    */
  def pickNewBlock(available: BitSet): Option[Request] = {

    def randomActivePiece: Option[Int] = {
      val activePieces = BitSet(piecesInProgress.keys.toSeq:_*)
      val intersection = activePieces & available
      intersection.toSeq.randomElement
    }

    def randomMissingPiece: Option[Int] =
      newPieces(available).toSeq.randomElement

    def pickPiece: Option[Int] =
      randomActivePiece
        .orElse(randomMissingPiece)

    def randomMissingBlock(piece: Int): Option[Int] =
      getBlocksIfPieceIncomplete(piece).flatMap {
        blocks =>
          val allBlocks = BitSet(0 until blocksPerPiece: _*)
          val blocksWeHave = BitSet(blocks.keys.toSeq: _*)
          val blocksMissing = allBlocks &~ blocksWeHave
          blocksMissing.toSeq.randomElement
      }

    for {
      piece <- pickPiece
      blockIndex <- randomMissingBlock(piece)
      blockBegin = blockIndex * BlockSize
    } yield Request(piece, blockBegin, BlockSize)
  }

  private def getBlocksIfPieceIncomplete(piece: Int): Option[Blocks] =
    if(pieceStatus(piece)) None
    else {
      def emptyBlocks: Blocks = mutable.Map.empty[Int, Bytes]
      Some(piecesInProgress.getOrElseUpdate(piece, emptyBlocks))
    }

  def report: ProgressReport = {
    def pieceProgress(index: Int): Option[Double] =
      piecesInProgress.get(index).map { _.size.toFloat / blocksPerPiece }

    val progressPerPiece = pieceStatus.zipWithIndex.map {
      case (complete, index) =>
        val progress: Double = if (complete) 1.0 else pieceProgress(index).getOrElse(0)
        (index, progress)
    }.toMap
    ProgressReport(totalPieces, progressPerPiece)
  }

}

object BitSetUtil {
  def fromBooleans(bools: Seq[Boolean]): BitSet = {
    val indexesOfTrue = bools.zipWithIndex.collect { case (true, i) => i }
    BitSet(indexesOfTrue: _*)
  }
}

case class ProgressReport(totalPieces: Int, progressPerPiece: Map[Int, Double]) {
  val overallProgress: Double = progressPerPiece.values.sum / totalPieces
}

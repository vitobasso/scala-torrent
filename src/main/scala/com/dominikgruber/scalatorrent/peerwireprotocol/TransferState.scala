package com.dominikgruber.scalatorrent.peerwireprotocol

import java.lang.System.currentTimeMillis

import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState._
import com.dominikgruber.scalatorrent.peerwireprotocol.message.Request
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.util.ByteUtil.Bytes

import scala.collection.{BitSet, mutable}
import scala.util.Random

/**
  * Keeps track of which pieces are done, missing or currently being downloaded.
  * Keeps data in memory for the pieces currently in progress.
  *
  * Picks a random missing block to be downloaded next.
  *   - prefers completing a piece in progress over starting a new one
  */
case class TransferState(metaInfo: MetaInfo) {

  val totalPieces: Int = metaInfo.fileInfo.numPieces
  val blocksPerPiece: Int = metaInfo.fileInfo.pieceLength / BlockSize

  /**
    * Marks which pieces from 0 to [[totalPieces]] we have completed
    */
  private val pieces: mutable.Seq[PieceStatus] = mutable.Seq.fill(totalPieces)(Empty)

  private val pending = new PendingRequests()

  /**
    * Add a received block to the transfer state.
    * @return bytes for a whole piece, if it's just been completed with the new block
    */
  def addBlock(piece: Int, block: Int, data: Bytes): Option[Bytes] = {
    pending.drop(piece, block)
    pieces(piece).received(block, data) match {
      case CompletePiece(bytes) =>
        pieces(piece) = Stored
        Some(bytes)
      case newStatus =>
        pieces(piece) = newStatus
        None
    }
  }

  /**
    * Because we know this piece has been stored before
    */
  def markPieceCompleted(piece: Int): Unit = {
    pending.drop(piece)
    pieces(piece) = Stored
  }

  /**
    * Because checksum validation failed
    */
  def resetPiece(piece: Int): Unit = {
    pieces(piece) = Empty
  }

  /**
    * @param piecesTheyHave pieces available in the remote peer
    * @return whether they have any block that we're missing
    */
  def isAnyPieceNewIn(piecesTheyHave: BitSet): Boolean =
    newPiecesIn(piecesTheyHave).nonEmpty

  /**
    * Create new requests for missing blocks and do something with them
    * @param piecesAvailable in the remote peer
    * @return
    *   - Some([[Request]]s) to get pieces we're missing from a remote peer; or
    *   - Some(empty) if out of request budget, even though we want of the piecesAvailable
    *   - None if none of the piecesAvailable interest us
    */
  def produceNewRequests(peer: PeerAddress, piecesAvailable: BitSet): Option[Seq[Request]] =
    nextRequests(peer, requestBudget(peer), piecesAvailable)

  /**
    * How many requests we can still add to the pending ones
    */
  private def requestBudget(peer: PeerAddress): Int =
    SimultaneousRequests - pending.size(peer)

  private def nextRequests(peer: PeerAddress, budget: Int, available: BitSet): Option[Seq[Request]] =
    if(budget <= 0) Some(Seq.empty)
    else nextRequest(peer, available) match {
      case Some(request) => Some(request +: nextRequests(peer, budget - 1, available).getOrElse(Nil))
      case None => None
    }

  private def nextRequest(peer: PeerAddress, available: BitSet): Option[Request] =
    pickNewBlock(available).map { request =>
      pending.add(peer, request)
      pieces(request.index) = pieces(request.index).requested(request.begin/BlockSize)
      request
    }

  /**
    * @param available in the remote peer
    * @return A [[Request]] to get a piece we're missing from a remote peer
    */
  def pickNewBlock(available: BitSet): Option[Request] = {

    def randomPieceInProgress: Option[Int] = {
      val indexesInProgress = pieces.zipWithIndex
        .collect { case (InProgressWithMissing(), index) => index }
      val inProgress = BitSet(indexesInProgress:_*)
      val intersection = inProgress & available
      intersection.toSeq.randomElement
    }

    def randomNewPiece: Option[Int] =
      newPiecesIn(available).toSeq.randomElement

    def pickPiece: Option[Int] =
      randomPieceInProgress
        .orElse(randomNewPiece)

    def randomMissingBlock(piece: Int): Option[Int] = {
      pieces(piece) match {
        case Empty => Some(Random.nextInt(blocksPerPiece))
        case InProgress(blocks) =>
          blocks.zipWithIndex
            .collect { case (block, index) if block == Missing => index}
            .randomElement
        case Stored => None
      }
    }

    for {
      piece <- pickPiece
      blockIndex <- randomMissingBlock(piece)
      blockBegin = blockIndex * BlockSize
    } yield Request(piece, blockBegin, BlockSize)
  }

  private def newPiecesIn(piecesTheyHave: BitSet): BitSet = {
    val missingIndexes = pieces.zipWithIndex.collect { case (Empty, i) => i }
    val piecesWeReMissing = BitSet(missingIndexes: _*)
    piecesTheyHave & piecesWeReMissing
  }

  implicit class PieceOps(piece: PieceStatus) {

    def requested(block: Int): PieceStatus = piece match {
      case Empty =>
        InProgress(emptyBlocks).requested(block)
      case InProgress(blocks) =>
        val newStatus = blocks(block).requested
        InProgress(blocks.updated(block, newStatus))
      case Stored => Stored
    }

    def received(newBlock: Int, data: Bytes): PieceStatus = piece match {
      case Empty =>
        InProgress(emptyBlocks).received(newBlock, data)
      case InProgress(blocks) =>
        InProgress(blocks.updated(newBlock, Received(data)))
      case Stored => Stored
    }

    def progress: Double = piece match {
      case Empty => 0
      case InProgress(blocks) => blocks.map(_.progress).sum / blocksPerPiece
      case Stored => 1
    }

    private val emptyBlocks = Seq.fill(blocksPerPiece)(Missing)
  }

  implicit class BlockOps(block: BlockStatus) {
    def requested: BlockStatus = block match {
      case Missing => Pending(currentTimeMillis)
      case Pending(_) => Pending(currentTimeMillis)
      case Received(bytes) => Received(bytes)
    }

    def progress: Double = block match {
      case Missing => 0
      case Pending(_) => 0
      case Received(_) => 1
    }
  }

  object CompletePiece {
    def unapply(status: PieceStatus): Option[Bytes] = status match {
      case InProgress(blocks) if blocks.size == blocksPerPiece =>
        blocks.foldLeft[Option[Bytes]](Some(Array())) {
          case (Some(bytes), Received(block)) =>
            Some(bytes ++ block)
          case _ => None
        }
      case _ => None
    }
  }

  object InProgressWithMissing {
    def unapply(status: PieceStatus): Boolean = status match {
      case InProgress(blocks) => blocks.contains(Missing)
      case _ => false
    }
  }

  def report: ProgressReport = {
    val progressPerPiece = pieces.map(_.progress)
    val overall = progressPerPiece.sum / totalPieces
    ProgressReport(overall, progressPerPiece)
  }

  implicit class SeqOps[T](seq: Seq[T]) {
    def randomElement: Option[T] = randomIndex.map(seq)
    def randomIndex: Option[Int] = seq.size match {
      case 0 => None
      case size => Some(Random.nextInt(size))
    }
  }

}

case object TransferState {
  val SimultaneousRequests: Int = 5 //pipelining: saturate tcp connection for performance

  sealed trait PieceStatus
  case object Empty extends PieceStatus
  case class InProgress(blocks: Seq[BlockStatus]) extends PieceStatus
  case object Stored extends PieceStatus

  sealed trait BlockStatus
  case object Missing extends BlockStatus
  case class Pending(since: Long) extends BlockStatus
  case class Received(bytes: Bytes) extends BlockStatus

  case class ProgressReport(overallProgress: Double, progressPerPiece: Seq[Double])
}

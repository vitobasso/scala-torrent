package com.dominikgruber.scalatorrent.peerwireprotocol

import java.lang.System.currentTimeMillis

import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState._
import com.dominikgruber.scalatorrent.peerwireprotocol.message.Request

import scala.collection.{BitSet, mutable}
import scala.concurrent.duration._
import scala.util.Random

case object TransferState {
  val SimultaneousRequests: Int = 5
  val RequestTTL: Duration = 10.seconds

  type Bytes = Array[Byte]

  sealed trait PieceStatus
  case object Missing extends PieceStatus
  case class InProgress(blocks: Seq[BlockStatus]) extends PieceStatus
  case object Stored extends PieceStatus

  sealed trait BlockStatus
  case object MissingBlock extends BlockStatus
  case class Pending(since: Long) extends BlockStatus
  case class Received(bytes: Bytes) extends BlockStatus

  case class ProgressReport(overallProgress: Double, progressPerPiece: Seq[Double])
}

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
  private val pieces: mutable.Seq[PieceStatus] = mutable.Seq.fill(totalPieces)(Missing)

  private val pendingRequests = mutable.Map.empty[Request, Long]

  /**
    * Add a received block to the transfer state.
    * @return bytes for a whole piece, if it's just been completed with the new block
    */
  def addBlock(piece: Int, block: Int, data: Bytes): Option[Bytes] = {
    removePending(piece, block)
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
    removePending(piece)
    pieces(piece) = Stored
  }

  /**
    * @param available in the remote peer
    * @return whether they have any block that we're missing
    */
  def isAnyPieceNew(available: BitSet): Boolean =
    newPieces(available).nonEmpty

  /**
    * Create new requests for missing blocks and do something with them
    */
  case class forEachNewRequest(piecesAvailable: BitSet)(whenHasElements: Request => Unit) {
    private val requests = nextRequests(requestBudget, piecesAvailable)
    def elseIfEmpty(whenEmpty: => Unit): Unit =
      if(requests.isEmpty)
        whenEmpty
      else requests.foreach { request: Request =>
        whenHasElements(request)
      }
  }

  private def removePending(piece: Int): Unit =
    pendingRequests.retain { case (req, _) => req.index != piece }

  private def removePending(piece: Int, block: Int): Unit =
    pendingRequests.retain { case (req, _) => req.index != piece || req.begin/BlockSize != block}

  /**
    * How many requests we can still add to the pending ones
    */
  private def requestBudget: Int = {
    def notTooOld(epoch: Long): Boolean = {
      val lifeTime: Duration = (currentTimeMillis - epoch).millis
      lifeTime < RequestTTL
    }
    pendingRequests.retain { case (_, t) => notTooOld(t) }
    SimultaneousRequests - pendingRequests.size
  }

  /**
    * @param available in the remote peer
    * @return [[Request]]s to get pieces we're missing from a remote peer
    */
  private def nextRequests(num: Int, available: BitSet): Seq[Request] =
    if(num <= 0) Nil
    else nextRequest(available) match {
      case Some(request) => request +: nextRequests(num - 1, available)
      case None => Nil
    }

  private def nextRequest(available: BitSet): Option[Request] =
    pickNewBlock(available).map { request =>
      pendingRequests += (request -> currentTimeMillis)
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
      newPieces(available).toSeq.randomElement

    def pickPiece: Option[Int] =
      randomPieceInProgress
        .orElse(randomNewPiece)

    def randomMissingBlock(piece: Int): Option[Int] = {
      pieces(piece) match {
        case Missing => Some(Random.nextInt(blocksPerPiece))
        case InProgress(blocks) =>
          blocks.zipWithIndex
            .collect { case (block, index) if block == MissingBlock => index}
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

  private def newPieces(available: BitSet): BitSet = {
    val missingIndexes = pieces.zipWithIndex.collect { case (Missing, i) => i }
    val missing = BitSet(missingIndexes: _*)
    available & missing
  }

  implicit class PieceOps(piece: PieceStatus) {

    def requested(block: Int): PieceStatus = piece match {
      case Missing =>
        InProgress(emptyBlocks).requested(block)
      case InProgress(blocks) =>
        val newStatus = blocks(block).requested
        InProgress(blocks.updated(block, newStatus))
      case Stored => Stored
    }

    def received(newBlock: Int, data: Bytes): PieceStatus = piece match {
      case Missing =>
        InProgress(emptyBlocks).received(newBlock, data)
      case InProgress(blocks) =>
        InProgress(blocks.updated(newBlock, Received(data)))
      case Stored => Stored
    }

    def progress: Double = piece match {
      case Missing => 0
      case InProgress(blocks) => blocks.map(_.progress).sum / blocksPerPiece
      case Stored => 1
    }

    private val emptyBlocks = Seq.fill(blocksPerPiece)(MissingBlock)
  }

  implicit class BlockOps(block: BlockStatus) {
    def requested: BlockStatus = block match {
      case MissingBlock => Pending(currentTimeMillis)
      case Pending(_) => Pending(currentTimeMillis)
      case Received(bytes) => Received(bytes)
    }

    def progress: Double = block match {
      case MissingBlock => 0
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
      case InProgress(blocks) => blocks.contains(MissingBlock)
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

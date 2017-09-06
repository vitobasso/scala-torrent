package com.dominikgruber.scalatorrent.transfer

import com.dominikgruber.scalatorrent.actor.Torrent.BlockSize
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.Request
import com.dominikgruber.scalatorrent.transfer.TransferStatus._
import com.dominikgruber.scalatorrent.util.{Mocks, UnitSpec}
import org.scalatest.PrivateMethodTester

import scala.collection.{BitSet, mutable}

class TransferStatusSpec extends UnitSpec with PrivateMethodTester {

  it should "begin with all blocks missing" in {
    val state = TransferStatus(meta)
    state.getPieces shouldBe Seq(Missing, Missing, Missing)
  }

  it should "mark a block" in {
    val state = TransferStatus(meta)
    state.addBlock(1, 1, data)

    val Seq(Missing, InProgress(blocks), Missing) = state.getPieces
    val Seq(MissingBlock, Received(observedData)) = blocks
    observedData should contain theSameElementsInOrderAs data
  }

  it should "mark a whole piece" in {
    val state = TransferStatus(meta)
    state.markPieceCompleted(2)

    state.getPieces shouldBe Seq(Missing, Missing, Stored)
  }

  it should "mark a piece when marking the last block" in {
    val state = TransferStatus(meta)
    state.addBlock(1, 0, data)
    state.addBlock(1, 1, data)

    state.getPieces shouldBe Seq(Missing, Stored, Missing)
  }

  it should "ignore a redundant mark" in {
    val state = TransferStatus(meta)

    state.addBlock(1, 0, data)
    state.addBlock(1, 0, data)
    val Seq(Missing, InProgress(blocks), Missing) = state.getPieces
    val Seq(Received(observedData), MissingBlock) = blocks
    observedData should contain theSameElementsInOrderAs data

    state.addBlock(1, 1, data)
    state.addBlock(1, 0, data)
    state.getPieces shouldBe Seq(Missing, Stored, Missing)
  }

  it should "only pick missing parts" in {
    val state = TransferStatus(meta)
    state.addBlock(0, 0, data)
    state.addBlock(0, 1, data)
    state.addBlock(1, 0, data)
    state.addBlock(1, 1, data)

    state.pickNewBlock(allAvailable) foreach {
      case Request(piece, _, _) => piece shouldBe 2
    }
  }

  it should "only pick missing blocks" in {
    val state = TransferStatus(meta)
    state.addBlock(0, 0, data)
    state.addBlock(1, 0, data)
    state.addBlock(2, 0, data)

    state.pickNewBlock(allAvailable) foreach {
      case Request(_, block, _) => block shouldBe 1 * BlockSize
    }
  }

  it should "pick None when complete" in {
    val state = TransferStatus(meta)
    state.addBlock(0, 0, data)
    state.addBlock(0, 1, data)
    state.addBlock(1, 0, data)
    state.addBlock(1, 1, data)
    state.addBlock(2, 0, data)
    state.addBlock(2, 1, data)

    state.pickNewBlock(allAvailable) shouldBe None
  }

  val meta: MetaInfo = Mocks.metaInfo(
    totalLength = 6 * BlockSize,
    pieceLength = 2 * BlockSize)
  val allAvailable = BitSet(0, 1, 2)
  val data: Array[Byte] = Array.empty[Byte]

  val pieces = PrivateMethod[mutable.Seq[PieceStatus]]('pieces)
  val pendingRequests = PrivateMethod[mutable.Map[Request, Long]]('pendingRequests)
  implicit class WhiteBox(sut: TransferStatus) {
    def getPieces: mutable.Seq[PieceStatus] =
      sut invokePrivate pieces()
  }

}

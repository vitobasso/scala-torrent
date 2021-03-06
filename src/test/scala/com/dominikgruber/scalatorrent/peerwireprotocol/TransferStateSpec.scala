package com.dominikgruber.scalatorrent.peerwireprotocol

import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState._
import com.dominikgruber.scalatorrent.peerwireprotocol.message.Request
import com.dominikgruber.scalatorrent.util.{Mocks, UnitSpec}
import org.scalatest.PrivateMethodTester

import scala.collection.{BitSet, mutable}
import scala.concurrent.duration._

class TransferStateSpec extends UnitSpec with PrivateMethodTester {

  it should "begin with all blocks missing" in {
    val state = TransferState(meta, config)
    state.getPieces shouldBe Seq(Empty, Empty, Empty)
  }

  it should "mark a block" in {
    val state = TransferState(meta, config)
    state.addBlock(1, 1, data)

    val Seq(Empty, InProgress(blocks), Empty) = state.getPieces
    val Seq(Missing, Received(observedData)) = blocks
    observedData should contain theSameElementsInOrderAs data
  }

  it should "mark a whole piece" in {
    val state = TransferState(meta, config)
    state.markPieceCompleted(2)

    state.getPieces shouldBe Seq(Empty, Empty, Stored)
  }

  it should "mark a piece when marking the last block" in {
    val state = TransferState(meta, config)
    state.addBlock(1, 0, data)
    state.addBlock(1, 1, data)

    state.getPieces shouldBe Seq(Empty, Stored, Empty)
  }

  it should "ignore a redundant mark" in {
    val state = TransferState(meta, config)

    state.addBlock(1, 0, data)
    state.addBlock(1, 0, data)
    val Seq(Empty, InProgress(blocks), Empty) = state.getPieces
    val Seq(Received(observedData), Missing) = blocks
    observedData should contain theSameElementsInOrderAs data

    state.addBlock(1, 1, data)
    state.addBlock(1, 0, data)
    state.getPieces shouldBe Seq(Empty, Stored, Empty)
  }

  it should "only pick missing parts" in {
    val state = TransferState(meta, config)
    state.addBlock(0, 0, data)
    state.addBlock(0, 1, data)
    state.addBlock(1, 0, data)
    state.addBlock(1, 1, data)

    state.pickNewBlock(allAvailable) foreach {
      case Request(piece, _, _) => piece shouldBe 2
    }
  }

  it should "only pick missing blocks" in {
    val state = TransferState(meta, config)
    state.addBlock(0, 0, data)
    state.addBlock(1, 0, data)
    state.addBlock(2, 0, data)

    state.pickNewBlock(allAvailable) foreach {
      case Request(_, block, _) => block shouldBe 1 * BlockSize
    }
  }

  it should "pick None when complete" in {
    val state = TransferState(meta, config)
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
  val config = TransferState.Config(5, 0.second)
  val allAvailable = BitSet(0, 1, 2)
  val data: Array[Byte] = Array.empty[Byte]

  val pieces = PrivateMethod[mutable.Seq[PieceStatus]]('pieces)
  implicit class WhiteBox(sut: TransferState) {
    def getPieces: mutable.Seq[PieceStatus] =
      sut invokePrivate pieces()
  }

}

package com.dominikgruber.scalatorrent.transfer

import com.dominikgruber.scalatorrent.actor.Torrent.BlockSize
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.Request
import com.dominikgruber.scalatorrent.util.{Mocks, UnitSpec}
import org.scalatest.PrivateMethodTester

import scala.collection.{BitSet, mutable}

class TransferStatusSpec extends UnitSpec with PrivateMethodTester {

  it should "begin with all blocks missing" in {
    val state = TransferStatus(meta)
    state.getPieceStatus shouldBe Seq(false, false, false)
    state.getPiecesInProgress shouldBe empty
  }

  it should "mark a block" in {
    val state = TransferStatus(meta)
    state.addBlock(1, 1, data)

    state.getPieceStatus shouldBe Seq(false, false, false)
    state.getPiecesInProgress shouldBe Map(1 -> Map(1 -> data))
  }

  it should "mark a whole piece" in {
    val state = TransferStatus(meta)
    state.completePiece(2)

    state.getPieceStatus shouldBe Seq(false, false, true)
    state.getPiecesInProgress shouldBe empty
  }

  it should "mark a piece when marking the last block" in {
    val state = TransferStatus(meta)
    state.addBlock(1, 0, data)
    state.addBlock(1, 1, data)

    state.getPieceStatus shouldBe Seq(false, true, false)
    state.getPiecesInProgress shouldBe empty
  }

  it should "ignore a redundant mark" in {
    val state = TransferStatus(meta)

    state.addBlock(1, 0, data)
    state.addBlock(1, 0, data)
    state.getPieceStatus shouldBe Seq(false, false, false)
    state.getPiecesInProgress shouldBe Map(1 -> Map(0 -> data))

    state.addBlock(1, 1, data)
    state.addBlock(1, 0, data)
    state.getPieceStatus shouldBe Seq(false, true, false)
    state.getPiecesInProgress shouldBe empty
  }

  it should "only pick missing parts" in {
    val state = TransferStatus(meta)
    state.addBlock(0, 0, data)
    state.addBlock(0, 1, data)
    state.addBlock(1, 0, data)
    state.addBlock(1, 1, data)

    state.pickNewBlock(allAvailable) foreach {
      case Request(piece, block, _) => piece shouldBe 2
    }
  }

  it should "only pick missing blocks" in {
    val state = TransferStatus(meta)
    state.addBlock(0, 0, data)
    state.addBlock(1, 0, data)
    state.addBlock(2, 0, data)

    state.pickNewBlock(allAvailable) foreach {
      case Request(piece, block, _) => block shouldBe 1 * BlockSize
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

  type Flags = mutable.Seq[Boolean]
  type Bytes = Vector[Byte]
  type Blocks = mutable.Map[Int, Bytes]
  val pieceStatus = PrivateMethod[Flags]('pieceStatus)
  val piecesInProgress = PrivateMethod[mutable.Map[Int, Blocks]]('piecesInProgress)
  implicit class WhiteBox(sut: TransferStatus) {
    def getPieceStatus: Flags =
      sut invokePrivate pieceStatus()
    def getPiecesInProgress: mutable.Map[Int, Blocks] =
      sut invokePrivate piecesInProgress()
  }

}

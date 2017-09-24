package com.dominikgruber.scalatorrent.storage

import com.dominikgruber.scalatorrent.util.ByteUtil._
import com.dominikgruber.scalatorrent.util.{Mocks, UnitSpec}
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scalax.io.{OutputConverter, OverwriteAll}

class FileManagerSingleSpec extends UnitSpec with MockitoSugar {

  def test(testBody: FileManagerFixture => Unit): Unit = {
    val meta = Mocks.fileMetaInfo(5, 2)
    val fixture = FileManagerFixture(meta, 3)
    testBody(fixture)
  }

  it should "do nothing if parent dir exists" in test { fixture =>
    val file = fixture.fileMocks("test-file")
    when(fixture.parentDir.exists) thenReturn true
    fixture.manager.initIfNew()
    verify(fixture.parentDir, never).doCreateDirectory()
    verify(file.resource, never).append(any[Array[Byte]])(any[OutputConverter[Array[Byte]]])
  }

  it should "create files filled with zeroes" in test { fixture =>
    val file = fixture.fileMocks("test-file")
    when(fixture.parentDir.exists) thenReturn false
    fixture.manager.initIfNew()
    verify(fixture.parentDir).doCreateDirectory()
    verify(file.resource).append(bytes("00 00 00"))
    verify(file.resource).append(bytes("00 00"))
  }

  it should "calculate file offsets" in test { fixture =>
    fixture.manager.fileSizes shouldBe List(("test-file", 5))
    fixture.manager.fileOffsets shouldBe List(0)
  }

  it should "load pieces" in test { fixture =>
    val file = fixture.fileMocks("test-file")
    fixture.manager.loadPiece(0)
    verify(file.bytes).lslice(0, 2)
    fixture.manager.loadPiece(1)
    verify(file.bytes).lslice(2, 4)
    fixture.manager.loadPiece(2)
    verify(file.bytes).lslice(4, 5)
  }

  it should "store pieces" in test { fixture =>
    val file = fixture.fileMocks("test-file")
    val twoBytes = bytes("01 02")
    val oneByte = bytes("01")
    fixture.manager.storePiece(0, twoBytes)
    verify(file.resource).patch(0, twoBytes, OverwriteAll)
    fixture.manager.storePiece(1, twoBytes)
    verify(file.resource).patch(2, twoBytes, OverwriteAll)
    fixture.manager.storePiece(2, twoBytes)
    verify(file.resource).patch(4, oneByte, OverwriteAll)
  }

  it should "not store more bytes than a piece" ignore test { fixture =>
    fixture.manager.storePiece(0, bytes("01 02 03"))
    //TODO validation
  }

  it should "not store a whole piece in a broken last position" ignore test { fixture =>
    fixture.manager.storePiece(2, bytes("01 02"))
    //TODO validation
  }

}

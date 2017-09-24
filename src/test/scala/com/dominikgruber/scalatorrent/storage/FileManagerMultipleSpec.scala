package com.dominikgruber.scalatorrent.storage

import com.dominikgruber.scalatorrent.metainfo.{FileInfo, MultiFileMetaInfo}
import com.dominikgruber.scalatorrent.util.ByteUtil._
import com.dominikgruber.scalatorrent.util.Mocks.infoHash
import com.dominikgruber.scalatorrent.util.UnitSpec
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

class FileManagerMultipleSpec extends UnitSpec with MockitoSugar {

  def test(testBody: FileManagerFixture => Unit): Unit = {
    val files = List(
      FileInfo(4, None, List("a")), //exactly one piece
      FileInfo(3, None, List("b")), //smaller than a piece
      FileInfo(2, None, List("dir1", "c")), // between 2 pieces
      FileInfo(1, None, List("d")),         // one of many files in one piece
      FileInfo(1, None, List("dir2", "e")), // one of many files in one piece
      FileInfo(7, None, List("dir2", "dir3", "f")), // spans 1 whole piece and parts of other 2
      FileInfo(4, None, List("dir2", "dir3", "g")) // 1 whole piece + part of another, last piece is incomplete

      //             0       1       2       3       4       5
      // pieces: |o-o-o-o|o-o-o-o|o-o-o-o|o-o-o-o|o-o-o-o|o-o-
      // files:  |o-o-o-o|o-o-o|o-o|o|o|o-o-o-o-o-o-o|o-o-o-o|
      //             a      b    c  d e       f          g
    )
    val meta = MultiFileMetaInfo(infoHash, 4, "", None, "", files)
    val fixture = FileManagerFixture(meta, 5)
    testBody(fixture)
  }

  it should "init a file in the parent folder" in test { fixture =>
    val file = fixture.fileMocks("a")
    when(fixture.parentDir.exists) thenReturn false
    fixture.manager.initIfNew()
    verify(fixture.parentDir).doCreateDirectory()
    verify(file.resource).append(bytes("00 00 00 00"))
  }

  it should "init a large file in a subfolder" in test { fixture => //large = larger than maxChunk
    val file = fixture.fileMocks("dir2/dir3/f")
    when(fixture.parentDir.exists) thenReturn false
    fixture.manager.initIfNew()
    verify(fixture.parentDir).doCreateDirectory()
    verify(file.resource).append(bytes("00 00 00 00 00"))
    verify(file.resource).append(bytes("00 00"))
  }

  it should "calculate file offsets" in test { fixture =>
    fixture.manager.fileSizes.map(_._2) shouldBe List(4, 3, 2, 1, 1, 7, 4)
    fixture.manager.fileOffsets shouldBe List(0, 4, 7, 9, 10, 11, 18)
  }

  it should "load a piece matching one file" in test { fixture =>
    fixture.manager.loadPiece(0)
    verify(fixture.fileMocks("a").bytes).lslice(0, 4)
  }

  it should "load a piece spanning 4 files" in test { fixture =>
    fixture.manager.loadPiece(2)
    verify(fixture.fileMocks("dir1/c").bytes).lslice(1, 2)
    verify(fixture.fileMocks("d").bytes).lslice(0, 1)
    verify(fixture.fileMocks("dir2/e").bytes).lslice(0, 1)
    verify(fixture.fileMocks("dir2/dir3/f").bytes).lslice(0, 1)
  }

  it should "load the last, incomplete, piece" in test { fixture =>
    fixture.manager.loadPiece(5)
    verify(fixture.fileMocks("dir2/dir3/g").bytes).lslice(2, 4)
  }

}

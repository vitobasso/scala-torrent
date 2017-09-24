package com.dominikgruber.scalatorrent.storage

import com.dominikgruber.scalatorrent.metainfo.{FileMetaInfo, MultiFileMetaInfo, SingleFileMetaInfo}
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar

import scalax.file.defaultfs.DefaultPath
import scalax.io._

/**
  * Mocks the internals of [[FileManager]] so that:
  *   - the test doesn't create actual files
  *   - we can [[org.mockito.Mockito.verify]] calls to the IO lib
  */
case class FileManagerFixture(meta: FileMetaInfo, maxChunk: Int) extends MockitoSugar {
  outer =>

  val parentDir = mock[DefaultPath]

  val files: Seq[String] = meta match {
    case s: SingleFileMetaInfo => Seq(s.name)
    case m: MultiFileMetaInfo => m.files.map(_.path.mkString("/"))
  }
  val fileMocks: Map[String, FileMock] = files.map { name => name -> FileMock(name) }.toMap

  val manager = new FileManager(meta, maxChunk){
    override val parentDir: DefaultPath = outer.parentDir
    override def resource(path: String): SeekableResource[SeekableByteChannel] =
      fileMocks(path).resource
  }

  case class FileMock(name: String) {
    val path = mock[DefaultPath]
    when(parentDir / name) thenReturn path
    val resource = mock[SeekableResource[SeekableByteChannel]]
    val bytes = mock[LongTraversable[Byte]]
    when(resource.bytes) thenReturn bytes
    private val slice = mock[LongTraversable[Byte]]
    when(bytes.lslice(anyLong, anyLong)) thenReturn slice
    private val array = Array.empty[Byte]
    when(slice.toArray) thenReturn array
  }

}

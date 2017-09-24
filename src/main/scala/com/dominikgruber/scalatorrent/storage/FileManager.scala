package com.dominikgruber.scalatorrent.storage

import com.dominikgruber.scalatorrent.metainfo.{FileMetaInfo, MultiFileMetaInfo, SingleFileMetaInfo}
import org.slf4j.{Logger, LoggerFactory}

import scalax.file.Path
import scalax.file.defaultfs.DefaultPath
import scalax.io.{OverwriteAll, Resource, SeekableByteChannel, SeekableResource}

/**
  * @param maxChunk For paging operations on whole files.
  *                 We want to transfer big chunks to and from disk each time,
  *                 but they still need to fit in memory.
  */
case class FileManager(meta: FileMetaInfo, maxChunk: Int) {

  val hash: String = meta.infoHashString
  val parentDir: DefaultPath = Path(hash)

  val totalSize: Long = meta.totalBytes
  val pieceSize: Int = meta.pieceLength
  val numPieces: Int = meta.numPieces

  protected def resource(path: String): SeekableResource[SeekableByteChannel] =
    Resource.fromFile((parentDir / path).jfile)

  def loadPiece(i: Int): Array[Byte] =
    for {
      slice <- FileSlices(i).toArray
      byte <- resource(slice.path).bytes
                .lslice(slice.fileFrom, slice.fileTo).toArray
    } yield byte

  def storePiece(i: Int, bytes: Array[Byte]) =
    for (slice <- FileSlices(i)) {
      val pieceSlice = bytes.slice(slice.pieceFrom, slice.pieceTo)
      resource(slice.path)
        .patch(slice.fileFrom, pieceSlice, OverwriteAll) //TODO validate that data fits in slice
    }

  val fileSizes: List[(String, Long)] =
    meta match {
      case s: SingleFileMetaInfo =>
        List((s.name, s.length))
      case m: MultiFileMetaInfo =>
        m.files.map { file =>
          (file.path.mkString("/"), file.length)
        }
    }

  def initIfNew(): Boolean =
    if(!parentDir.exists) {
      parentDir.doCreateDirectory()
      initFiles()
      true
    } else false

  private def initFiles(): Unit =
    fileSizes.foreach {
      case (file, length) =>
        if(!(parentDir / file).exists)
          initFile(file, length)
        else
          log.warn(s"Was about to initialize $file, but it already exists!")
    }

  private def initFile(path: String, length: Long): Unit = {
    log.info(s"Initializing file $path with $length zeros")
    val res = resource(path)
    val numWholePages: Int = (length/maxChunk).toInt

    val zeros: Array[Byte] = Array.fill(maxChunk)(0.toByte)
    for (_ <- 0 until numWholePages) {
      res.append(zeros)
    }

    //last piece may be shorter
    val remaining: Int = (length % maxChunk).toInt
    val zerosRemaining: Array[Byte] = Array.fill(remaining)(0.toByte)
    res.append(zerosRemaining)
  }

  /**
    * Byte offset of each file as part of the whole multi-file torrent
    */
  val fileOffsets: List[Long] = fileSizes.foldRight(List.empty[Long]) {
    case ((_, length: Long), acc: List[Long]) => acc match {
      case nextOffset :: _ => nextOffset - length :: acc
      case Nil => totalSize - length :: acc
    }
  }

  /**
    * Given the correspondence between:
    *   - the set of all pieces
    *   - the set of files (also in continuous bytes together)
    * finds slices (begins and ends) for every intersection between files and the given piece.
    */
  case class FileSlices(piece: Int) extends Iterator[FileSlice] {

    /**
      * Index of the first file in [[fileOffsets]] that has bytes in the given piece
      */
    private def firstFile: Int = {
      def search(from: Int, to: Int): Int = {
        if(to - from == 1)
          from
        else {
          val mid = (from + to) / 2
          if(piece >= fileOffsets(mid))
            search(mid, to)
          else
            search(from, mid)
        }
      }
      search(0, fileOffsets.length)
    }

    var nextIndex: Int = firstFile

    def hasNext: Boolean =
      nextIndex < fileOffsets.size &&
        fileOffsets(nextIndex) < pieceEnd

    val pieceBegin: Int = piece * pieceSize
    val pieceEnd: Int = pieceBegin + pieceSize

    def next: FileSlice = {
      val (path, fileSize) = fileSizes(nextIndex)
      val fileOffset = fileOffsets(nextIndex)
      val beginInFile = (pieceBegin - fileOffset) max 0
      val endInFile = (pieceEnd - fileOffset) min fileSize
      val beginInPiece = (fileOffset + beginInFile - pieceBegin).toInt
      val endInPiece = (fileOffset + endInFile - pieceBegin).toInt
      nextIndex += 1
      FileSlice(path, beginInFile, endInFile, beginInPiece, endInPiece)
    }
  }

  //TODO use Ranges ?
  case class FileSlice(path: String, fileFrom: Long, fileTo: Long, pieceFrom: Int, pieceTo: Int)

  private lazy val log: Logger = LoggerFactory.getLogger(getClass)

}
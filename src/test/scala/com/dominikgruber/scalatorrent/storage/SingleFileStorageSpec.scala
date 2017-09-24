package com.dominikgruber.scalatorrent.storage

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.metainfo.FileMetaInfo
import com.dominikgruber.scalatorrent.storage.Storage._
import com.dominikgruber.scalatorrent.util.ByteUtil.bytes
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

import scala.collection.BitSet
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scalax.file.Path

class SingleFileStorageSpec extends ActorSpec {
  outer =>

  val testFileName = "storage-test-file"
  val meta: FileMetaInfo = Mocks.fileMetaInfo(7, 2, testFileName)
  val hash: String = meta.infoHashString
  val parentDir = Path(hash)
  val dataFile = parentDir / testFileName
  val statusFile = parentDir / s"$hash.status"
  val torrent = TestProbe("torrent")
  val patience = 30 seconds

  "a Storage actor" must {

    "initialize the file with zeroes" in {
      assumeClean
      fixture { storage =>
        storage ! Load(0)
        expectLoaded(0, "00 00")
        storage ! Load(1)
        expectLoaded(1, "00 00")
        storage ! Load(2)
        expectLoaded(2, "00 00")
        storage ! Load(3)
        expectLoaded(3, "00")
        dataFile.exists shouldBe true
      }
    }

    "leave an existing file untouched" in {
      assumeClean
      dataFile.write("1234567")
      assume(dataFile.exists)
      fixture { storage =>
        storage ! Load(0)
        expectLoaded(0, "31 32")
        storage ! Load(1)
        expectLoaded(1, "33 34")
        storage ! Load(2)
        expectLoaded(2, "35 36")
        storage ! Load(3)
        expectLoaded(3, "37")
      }
    }

    "store pieces in non-sequential order" in {
      assumeClean
      fixture { storage =>
        storage ! Store(2, bytes("33 34"))
        storage ! Store(0, bytes("31 32"))

        storage ! Load(0)
        expectLoaded(0, "31 32")
        storage ! Load(1)
        expectLoaded(1, "00 00")
        storage ! Load(2)
        expectLoaded(2, "33 34")
        storage ! Load(3)
        expectLoaded(3, "00")
      }
    }

    "store a piece in the last position" in {
      assumeClean
      fixture { storage =>
        storage ! Store(3, bytes("31"))

        storage ! Load(0)
        expectLoaded(0, "00 00")
        storage ! Load(1)
        expectLoaded(1, "00 00")
        storage ! Load(2)
        expectLoaded(2, "00 00")
        storage ! Load(3)
        expectLoaded(3, "31")
      }
    }

    "overwrite a piece" in {
      assumeClean
      fixture { storage =>
        storage ! Store(2, bytes("33 34"))
        storage ! Store(2, bytes("31 32"))

        storage ! Load(0)
        expectLoaded(0, "00 00")
        storage ! Load(1)
        expectLoaded(1, "00 00")
        storage ! Load(2)
        expectLoaded(2, "31 32")
        storage ! Load(3)
        expectLoaded(3, "00")
      }
    }

    "return the status" in {
      assumeClean
      fixture { storage =>
        storage ! StatusPlease
        expectMsg(patience, Status(BitSet.empty))

        storage ! Store(1, bytes("31 32"))
        storage ! Store(2, bytes("33 34"))

        storage ! StatusPlease
        expectMsg(patience, Status(BitSet(1, 2)))

        storage ! Store(3, bytes("35"))

        storage ! StatusPlease
        expectMsg(patience, Status(BitSet(1, 2, 3)))
      }
    }

    "remove metadata when complete" in {
      assumeClean
      fixture { storage =>
        wait[Status](storage) //wait for file initialization
        statusFile.size shouldBe a[Some[Long]] //status metadata added to file

        storage ! Store(0, bytes("31 32"))
        storage ! Store(1, bytes("33 34"))
        storage ! Store(2, bytes("35 36"))
        wait[Status](storage)
        statusFile.size shouldBe a[Some[Long]] //not removed yet

        storage ! Store(3, bytes("37"))
        wait[Complete.type](storage)
        statusFile.size
        statusFile.size shouldBe None //removed when complete

        storage ! Store(3, bytes("37"))
        wait[Complete.type](storage)
        statusFile.size shouldBe None //not added again
      }
    }

  }

  def assumeClean: Unit = {
    assume(!parentDir.exists)
  }

  def expectLoaded(index: Int, byteStr: String): Unit =
    expectMsgPF(patience) {
      case Loaded(`index`, data) =>
        data should contain theSameElementsInOrderAs bytes(byteStr)
    }

  def fixture(test: ActorRef => Unit): Unit = {
    val storage = {
      def createActor = new Storage(meta) {
        override val files: FileManager = {
          val maxChunk: Int = 5 // > pieceSize but < totalSize
          FileManager(meta, maxChunk)
        }
      }
      system.actorOf(Props(createActor), "storage")
    }
    try {
      test(storage)
    } finally {
      syncStop(storage)
      parentDir.deleteRecursively()
    }
  }

  /**
    * Wait for previous message to be finished processing
    * (if we get an answer to a new message, StatusPlease, it means the previous message is also done)
    */
  def wait[M: ClassTag](storage: ActorRef) = {
    storage ! StatusPlease
    expectMsgType[M](patience)
  }

}

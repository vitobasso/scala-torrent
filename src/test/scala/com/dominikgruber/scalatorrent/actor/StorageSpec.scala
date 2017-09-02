package com.dominikgruber.scalatorrent.actor

import java.nio.charset.StandardCharsets._
import java.nio.file.{Files, Path, Paths}

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.actor.Storage._
import com.dominikgruber.scalatorrent.metainfo.FileMetaInfo
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

import scala.collection.BitSet

class StorageSpec extends ActorSpec {
  outer =>

  private val fileName = "storage-test-file"
  val path: Path = Paths.get(fileName)
  val meta: FileMetaInfo = Mocks.fileMetaInfo(7, 2, fileName)
  val torrent = TestProbe("torrent")

  "a Storage actor" must {

    "initialize the file with zeroes" in {
      Files.exists(path) shouldBe false
      withCleanContext { storage =>
        storage ! Load(0)
        expectLoaded(0, "00 00")
        storage ! Load(1)
        expectLoaded(1, "00 00")
        storage ! Load(2)
        expectLoaded(2, "00 00")
        storage ! Load(3)
        expectLoaded(3, "00")
        Files.exists(path) shouldBe true
      }
    }

    "leave an existing file untouched" in {
      Files.exists(path) shouldBe false
      Files.write(path, "1234567".getBytes(ISO_8859_1))
      Files.exists(path) shouldBe true
      withCleanContext { storage =>
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
      Files.exists(path) shouldBe false
      withCleanContext { storage =>
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
      Files.exists(path) shouldBe false
      withCleanContext { storage =>
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
      Files.exists(path) shouldBe false
      withCleanContext { storage =>
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
      Files.exists(path) shouldBe false
      withCleanContext { storage =>
        storage ! StatusPlease
        expectMsg(Status(BitSet.empty))

        storage ! Store(1, bytes("31 32"))
        storage ! Store(2, bytes("33 34"))

        storage ! StatusPlease
        expectMsg(Status(BitSet(1, 2)))
      }
    }

  }

  def expectLoaded(index: Int, byteStr: String): Unit =
    expectMsgPF() {
      case Loaded(`index`, data) =>
        data should contain theSameElementsInOrderAs bytes(byteStr)
    }

  def withCleanContext(test: ActorRef => Unit): Unit = {
    val storage = {
      def createActor = new Storage(meta) {
        override val pageSize: Int = 5 // > pieceSize but < totalSize
      }
      system.actorOf(Props(createActor), "storage")
    }
    try {
      test(storage)
    } finally {
      syncStop(storage)
      Files.delete(path)
    }
  }

  def bytes(str: String): Array[Byte] = {
    str.split(" ").map(Integer.parseInt(_, 16).toByte)
  }

}

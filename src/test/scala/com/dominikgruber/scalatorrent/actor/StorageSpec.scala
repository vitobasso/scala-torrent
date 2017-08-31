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
        expectMsg(Loaded(0, bytes("00 00")))
        storage ! Load(1)
        expectMsg(Loaded(1, bytes("00 00")))
        storage ! Load(2)
        expectMsg(Loaded(2, bytes("00 00")))
        storage ! Load(3)
        expectMsg(Loaded(3, bytes("00")))
        Files.exists(path) shouldBe true
      }
    }

    "leave an existing file untouched" in {
      Files.exists(path) shouldBe false
      Files.write(path, "1234567".getBytes(ISO_8859_1))
      Files.exists(path) shouldBe true
      withCleanContext { storage =>
        storage ! Load(0)
        expectMsg(Loaded(0, bytes("31 32")))
        storage ! Load(1)
        expectMsg(Loaded(1, bytes("33 34")))
        storage ! Load(2)
        expectMsg(Loaded(2, bytes("35 36")))
        storage ! Load(3)
        expectMsg(Loaded(3, bytes("37")))
      }
    }

    "store pieces in non-sequential order" in {
      Files.exists(path) shouldBe false
      withCleanContext { storage =>
        storage ! Store(2, bytes("33 34"))
        storage ! Store(0, bytes("31 32"))

        storage ! Load(0)
        expectMsg(Loaded(0, bytes("31 32")))
        storage ! Load(1)
        expectMsg(Loaded(1, bytes("00 00")))
        storage ! Load(2)
        expectMsg(Loaded(2, bytes("33 34")))
        storage ! Load(3)
        expectMsg(Loaded(3, bytes("00")))
      }
    }

    "store a piece in the last position" in {
      Files.exists(path) shouldBe false
      withCleanContext { storage =>
        storage ! Store(3, bytes("31"))

        storage ! Load(0)
        expectMsg(Loaded(0, bytes("00 00")))
        storage ! Load(1)
        expectMsg(Loaded(1, bytes("00 00")))
        storage ! Load(2)
        expectMsg(Loaded(2, bytes("00 00")))
        storage ! Load(3)
        expectMsg(Loaded(3, bytes("31")))
      }
    }

    "overwrite a piece" in {
      Files.exists(path) shouldBe false
      withCleanContext { storage =>
        storage ! Store(2, bytes("33 34"))
        storage ! Store(2, bytes("31 32"))

        storage ! Load(0)
        expectMsg(Loaded(0, bytes("00 00")))
        storage ! Load(1)
        expectMsg(Loaded(1, bytes("00 00")))
        storage ! Load(2)
        expectMsg(Loaded(2, bytes("31 32")))
        storage ! Load(3)
        expectMsg(Loaded(3, bytes("00")))
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

  def withCleanContext(test: ActorRef => Unit): Unit = {
    val storage = {
      def createActor = new Storage(meta)
      system.actorOf(Props(createActor), "storage")
    }
    try {
      test(storage)
    } finally {
      syncStop(storage)
      Files.delete(path)
    }
  }

  def bytes(str: String): Vector[Byte] = {
    str.split(" ").map(Integer.parseInt(_, 16).toByte).toVector
  }

}

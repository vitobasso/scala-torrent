package com.dominikgruber.scalatorrent.peerwireprotocol.network

import akka.util.ByteString
import com.dominikgruber.scalatorrent.peerwireprotocol.message._
import com.dominikgruber.scalatorrent.peerwireprotocol.network.MessageBuffer._
import com.dominikgruber.scalatorrent.util.ByteUtil.bytes
import com.dominikgruber.scalatorrent.util.UnitSpec

class MessageBufferSpec extends UnitSpec {

  it should "receive a Keepalive" in {
    bytes("00 00 00 00") shouldProduceMessage
      Result(List(KeepAlive()))
  }

  it should "receive a double Keepalive" in  {
    bytes("00 00 00 00 00 00 00 00") shouldProduceMessage
      Result(List(KeepAlive(), KeepAlive()))
  }

  it should "receive a Bifield + Unchoke" in  {
    val res = bytes("00 00 00 09 05 FF FF FF FF FF FF FF FF 00 00 00 01 01").testMessage
    res.messages.size shouldBe 2
    res.messages.head shouldBe a[Bitfield]
    res.messages(1) shouldBe Unchoke()
  }

  it should "receive a Bifield + short rubbish" in  {
    val res = bytes("00 00 00 08 05 FF FF FF FF FF FF FF FF").testMessage
    res.messages.size shouldBe 1
    res.messages.head shouldBe a[Bitfield]
    res.rubbish shouldBe Some(bytes("FF").toVector)
  }

  it should "receive a Bifield + long rubbish" in  {
    val res = bytes("00 00 00 08 05 FF FF FF FF FF FF FF FF FF FF FF FF").testMessage
    res.messages.size shouldBe 1
    res.messages.head shouldBe a[Bitfield]
    res.rubbish shouldBe Some(bytes("FF FF FF FF FF").toVector)
  }

  it should "drop rubbish if length is negative" in  {
    bytes("FF 00 00 00 01 01") shouldProduceMessage Result(rubbish = bytes("FF 00 00 00 01 01").toVector)
  }

  it should "receive a Piece in parts" in {
    val part1 = bytes("00 00 00 15 07 00 00 00 00 00 00 00 00 01 02 03 04")
    val part2 = bytes("05 06 07 08 09 0A 0B 0C")
    val buffer = new MessageBuffer
    def test(bytes: Array[Byte]) = buffer.receiveBytes(MessageMode)(ByteString(bytes))

    test(part1) shouldBe
      Result(Nil)
    test(part2) shouldBe
      Result(List(Piece(0, 0, bytes("01 02 03 04 05 06 07 08 09 0A 0B 0C").toVector)))
  }

  it should "receive a Handshake" in {
    val handshake = bytes(
      """13
        |42 69 74 54 6F 72 72 65 6E 74 20 70 72 6F 74 6F 63 6F 6C
        |00 00 00 00 00 10 00 05
        |5B CB 7E 72 AE C7 74 99 76 22 AF 7D E5 47 1D 71 E1 7F 1D B8
        |2D 44 45 31 33 46 30 2D 58 76 76 35 78 57 48 66 69 49 69 2D
        |""".stripMargin.replace("\n", " "))
    val result = handshake.testHandshake
    result.messages.size shouldBe 1
    result.messages.head shouldBe a[Handshake]
  }

  implicit class TestTemplate(bytes: Array[Byte]) {
    def test[M <: MessageOrHandshake](mode: Mode[M]): Result[M] = {
      val buffer = new MessageBuffer
      buffer.receiveBytes(mode)(ByteString(bytes))
    }
    def testMessage = test(MessageMode)
    def testHandshake = test(HandshakeMode)
    def shouldProduceMessage(expected: Result[Message]): Unit = {
      test(MessageMode) shouldBe expected
    }
    def shouldProduceHandshake(expected: Result[Handshake]): Unit = {
      test(HandshakeMode) shouldBe expected
    }
  }

}

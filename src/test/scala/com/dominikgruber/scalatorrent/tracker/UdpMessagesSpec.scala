package com.dominikgruber.scalatorrent.tracker

import com.dominikgruber.scalatorrent.tracker.udp._
import com.dominikgruber.scalatorrent.tracker.udp.UdpEncoding
import com.dominikgruber.scalatorrent.util.ByteUtil._
import com.dominikgruber.scalatorrent.util.UnitSpec
import sbinary.Operations.fromByteArray

import scala.util.Success

class UdpMessagesSpec extends UnitSpec {

  it should "validate an InfoHash" in {
    val twentyBytes = bytes("01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20")
    val valid = InfoHash.validate(twentyBytes)
    assertThrows[IllegalArgumentException]{
      val nineteenBytes = bytes("01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19")
      val invalid = InfoHash.validate(nineteenBytes)
    }
  }

  it should "validate a PeerId" in {
    val twentyBytes = bytes("01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19 20")
    val valid = PeerId.validate(twentyBytes)
    assertThrows[IllegalArgumentException]{
      val nineteenBytes = bytes("01 02 03 04 05 06 07 08 09 10 11 12 13 14 15 16 17 18 19")
      val invalid = PeerId.validate(nineteenBytes)
    }
  }

  it should "encode a ConnectRequest" in {
    val input = ConnectRequest(TransactionId(1))
    UdpEncoding.encode(input) shouldBe bytes("00 00 04 17 27 10 19 80 00 00 00 00 00 00 00 01")
  }

  it should "decode a ConnectResponse" in {
    val input = bytes("00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 02")
    UdpEncoding.decode(input) shouldBe Success(ConnectResponse(TransactionId(1), ConnectionId(2)))
  }

  it should "encode a AnnounceRequest" in {
    val hash = InfoHash.validate(bytes("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 03"))
    val peer = PeerId.validate(bytes("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 04"))
    val input = AnnounceRequest(ConnectionId(2), TransactionId(1),
      hash, peer, 5, 6, 7, AnnounceEvent.Started, 8, 9, 10, 11)
    UdpEncoding.encode(input) shouldBe bytes {
      "00 00 00 00 00 00 00 02 " +  //connection_id
      "00 00 00 01 " +  //action
      "00 00 00 01 " +  //transaction_id
      "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 03 " +  //info_hash
      "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 04 " +  //peer_id
      "00 00 00 00 00 00 00 05 " +  //downloaded
      "00 00 00 00 00 00 00 06 " +  //left
      "00 00 00 00 00 00 00 07 " +  //uploaded
      "00 00 00 02 " +  //event
      "00 00 00 08 " +  //IP address
      "00 00 00 09 " +  //key
      "00 00 00 0A " +  //num_want
      "00 0B"   //port
    }
  }

  it should "decode a AnnounceResponse" in {
    val input = bytes {
      "00 00 00 01 " + //action
      "00 00 00 01 " + //transaction_id
      "00 00 00 02 " + //interval
      "00 00 00 03 " + //leechers
      "00 00 00 04 " + //seeders
      "00 00 00 05 " + //IP address
      "00 06 " +       //TCP port
      "00 00 00 07 " + //IP address
      "00 08"}         //TCP port
    val peers = Seq(PeerAddress("0.0.0.5", 6), PeerAddress("0.0.0.7", 8))
    UdpEncoding.decode(input) shouldBe Success(AnnounceResponse(TransactionId(1), 2, 3, 4, peers))
  }

  it should "decode a port from an unsigned short" in {
    val peerBytes = bytes("00 00 00 00 FF FF")
    val peer = fromByteArray(peerBytes)(UdpEncoding.readsPeerAddr)
    peer.port shouldBe 65535
  }
  it should "decode an ip from 32 bits" in {
    val peerBytes = bytes("FF FF FF FF 00 00")
    val peer = fromByteArray(peerBytes)(UdpEncoding.readsPeerAddr)
    peer.ip shouldBe "255.255.255.255"
  }

}

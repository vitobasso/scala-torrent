package com.dominikgruber.scalatorrent.tracker

import com.dominikgruber.scalatorrent.tracker.UDPMessages._
import com.dominikgruber.scalatorrent.util.UnitSpec

class UDPMessagesSpec extends UnitSpec {

  it should "encode a ConnectRequest" in {
    val input = ConnectRequest(TransactionId(1))
    Connect.encode(input) shouldBe bytes("00 00 04 17 27 10 19 80 00 00 00 00 00 00 00 01")
  }

  it should "decode a ConnectResponse" in {
    val input = bytes("00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 02")
    Connect.decode(input) shouldBe ConnectResponse(TransactionId(1), ConnectionId(2))
  }

  it should "encode a AnnounceRequest" in {
    val hash = InfoHash(bytes("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 03"))
    val peer = PeerId(bytes("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 04"))
    val input = AnnounceRequest(ConnectionId(2), TransactionId(1), hash, peer, 5, 6, 7, Started, 8, 9, 10, 11)
    Announce.encode(input) shouldBe bytes {
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
    val peers = Seq(UDPMessages.PeerAddress(5, 6), UDPMessages.PeerAddress(7, 8))
    Announce.decode(input) shouldBe AnnounceResponse(TransactionId(1), 2, 3, 4, peers)
  }

  def bytes(str: String): Array[Byte] = {
    str.split(" ").map(Integer.parseInt(_, 16).toByte)
  }

}

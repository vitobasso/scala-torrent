package com.dominikgruber.scalatorrent.dht.message

import com.dominikgruber.scalatorrent.dht.message.DhtMessage._
import com.dominikgruber.scalatorrent.util.UnitSpec

/**
  * Raw strings copied from: http://www.bittorrent.org/beps/bep_0005.html
  */
class KrpcEncodingSpec extends UnitSpec {

  def testCase(name: String, raw: String, msg: DhtMessage.Message): Unit = {
    name should "be encoded" in {
      KrpcEncoding.encode(msg) shouldBe Right(raw)
    }
    it should "be decoded" in {
      KrpcEncoding.decode(raw) shouldBe Right(msg)
    }
  }

  val transactionId = TransactionId("aa")
  val localNode = nodeId("abcdefghij0123456789")
  val remoteNode = nodeId("mnopqrstuvwxyz123456")

  testCase("Ping",
    raw = "d1:ad2:id20:abcdefghij0123456789e1:q4:ping1:t2:aa1:y1:qe",
    msg = Ping(transactionId, localNode)
  )

  testCase("Pong",
    raw = "d1:rd2:id20:mnopqrstuvwxyz123456e1:t2:aa1:y1:re",
    msg = Pong(transactionId, remoteNode)
  )

  testCase("FindNode",
    raw = "d1:ad2:id20:abcdefghij01234567896:target20:mnopqrstuvwxyz123456e1:q9:find_node1:t2:aa1:y1:qe",
    msg = FindNode(transactionId, localNode, remoteNode)
  )

  val rawAddr1 = "123456"
  val addr1: PeerInfo = {
    val ip = Ip.parse("49.50.51.52").right.get //1234
    val port = Port(0x00003536) //56
    Address(ip, port)
  }

  val rawAddr2 = "abcdef"
  val addr2: PeerInfo = {
    val ip = Ip.parse("97.98.99.100").right.get //abcd
    val port = Port(0x00006566) //ef
    Address(ip, port)
  }

  val nodeId1 = "nodeid1-has-20-chars"
  val nodeId2 = "nodeid2-has-20-chars"
  val rawNodes = s"$nodeId1$rawAddr1$nodeId2$rawAddr2"
  val nodes: Seq[NodeInfo] = {
    val node1 = NodeInfo(nodeId(nodeId1), Address(addr1.ip, addr1.port))
    val node2 = NodeInfo(nodeId(nodeId2), Address(addr2.ip, addr2.port))
    Seq(node1, node2)
  }

  testCase("NodesFound",
    raw = s"d1:rd2:id20:abcdefghij01234567895:nodes52:${rawNodes}e1:t2:aa1:y1:re",
    msg = NodesFound(transactionId, localNode, nodes)
  )

  val infoHash: InfoHash = InfoHash.validate("mnopqrstuvwxyz123456").right.get

  testCase("GetPeers",
    raw = "d1:ad2:id20:abcdefghij01234567899:info_hash20:mnopqrstuvwxyz123456e1:q9:get_peers1:t2:aa1:y1:qe",
    msg = GetPeers(transactionId, localNode, infoHash)
  )

  val token = Token("aoeusnth")
  val rawPeers = s"l6:${rawAddr1}6:${rawAddr2}e"
  val peers = Seq(addr1, addr2)

  testCase("PeersFound",
    raw = s"d1:rd2:id20:abcdefghij01234567895:token8:aoeusnth6:values${rawPeers}e1:t2:aa1:y1:re",
    msg = PeersFound(transactionId, localNode, Some(token), peers, Seq.empty)
  )

  testCase("PeersFound + nodes",
    raw = s"d1:rd2:id20:abcdefghij01234567895:nodes52:${rawNodes}5:token8:aoeusnth6:values${rawPeers}e1:t2:aa1:y1:re",
    msg = PeersFound(transactionId, localNode, Some(token), peers, nodes)
  )

  testCase("PeersFound, + nodes, no token",
    raw = s"d1:rd2:id20:abcdefghij01234567895:nodes52:${rawNodes}6:values${rawPeers}e1:t2:aa1:y1:re",
    msg = PeersFound(transactionId, localNode, None, peers, nodes)
  )

  testCase("PeersFound, no token",
    raw = s"d1:rd2:id20:abcdefghij01234567896:values${rawPeers}e1:t2:aa1:y1:re",
    msg = PeersFound(transactionId, localNode, None, peers, Seq.empty)
  )

  testCase("PeersNotFound",
    raw = s"d1:rd2:id20:abcdefghij01234567895:nodes52:${rawNodes}5:token8:aoeusnthe1:t2:aa1:y1:re",
    msg = PeersNotFound(transactionId, localNode, token, nodes)
  )

  testCase("AnnouncePeer",
    raw = "d1:ad2:id20:abcdefghij01234567899:info_hash20:mnopqrstuvwxyz1234564:porti6881e5:token8:aoeusnthe1:q13:announce_peer1:t2:aa1:y1:qe",
    msg = AnnouncePeer(transactionId, localNode, infoHash, Some(Port(6881)), token)
  )

  val rawPeerReceived = "d1:rd2:id20:mnopqrstuvwxyz123456e1:t2:aa1:y1:re"
  val peerReceived = PeerReceived(transactionId, remoteNode)
  "PeerReceived" should "be encoded" in {
      KrpcEncoding.encode(peerReceived) shouldBe Right(rawPeerReceived)
  }
  it should "be decoded" in {
    KrpcEncoding.decode(rawPeerReceived) shouldBe Right(Pong(transactionId, remoteNode)) //FIXME a raw Pong is identical to AnnouncePeerResponse
  }

  val unknown = "7:unknown5:value"
  "NodesFound + unknown field" should "be decoded" in {
    val raw = s"d1:rd2:id20:abcdefghij01234567895:nodes52:$rawNodes${unknown}e1:t2:aa1:y1:re"
    val msg = NodesFound(transactionId, localNode, nodes)
    KrpcEncoding.decode(raw) shouldBe Right(msg)
  }

  def nodeId(str: String): NodeId = NodeId.validate(str).right.get
}

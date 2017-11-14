package com.dominikgruber.scalatorrent

import com.dominikgruber.scalatorrent.Coordinator.ConnectToPeer
import com.dominikgruber.scalatorrent.PeerFinder.{FindPeers, PeersFound}
import com.dominikgruber.scalatorrent.Torrent._
import com.dominikgruber.scalatorrent.metainfo.MetaInfo
import com.dominikgruber.scalatorrent.storage.Storage.Status
import com.dominikgruber.scalatorrent.tracker.PeerAddress
import com.dominikgruber.scalatorrent.util.Mocks

import scala.collection.BitSet

class TorrentFindingPeersSpec extends TorrentSpec {

  override val meta: MetaInfo = Mocks.metaInfo(
    totalLength = 8 * BlockSize,
    pieceLength = 2 * BlockSize)
  val allAvailable = BitSet(0, 1, 2, 3)
  val totalBlocks: Long = meta.fileInfo.totalBytes/BlockSize

  "a Torrent actor, when finding peers" must {

    "ask for peers on initialization" in {
      torrent ! Status(BitSet.empty)
      peerFinder expectMsg FindPeers
    }

    "create peer connections" in {
      val peer1 = PeerAddress("ip1", 0)
      val peer2 = PeerAddress("ip2", 0)
      torrent ! PeersFound(Set(peer1, peer2))

      coordinator expectMsg ConnectToPeer(peer1, meta)
      coordinator expectMsg ConnectToPeer(peer2, meta)
    }

  }

}
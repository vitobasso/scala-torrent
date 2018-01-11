package com.dominikgruber.scalatorrent

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.metainfo.{MetaInfo, PieceChecksum}
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

import scala.concurrent.duration._

trait TorrentSpec extends ActorSpec {
  outer =>

  def meta: MetaInfo
  val peerFinder = TestProbe("peer-finder")
  val storage = TestProbe("storage")
  val coordinator = TestProbe("coordinator")
  val stateConfig = TransferState.Config(5, 10.seconds)
  val config = Torrent.Config(stateConfig, mock[PeerFinder.Config])

  lazy val torrent: ActorRef = {
    def createActor = new Torrent(meta, coordinator.ref, config) {
      override lazy val peerFinder: ActorRef = outer.peerFinder.ref
      override lazy val storage: ActorRef = outer.storage.ref
      override val checksum: PieceChecksum = Mocks.checksum
    }
    system.actorOf(Props(createActor), "torrent")
  }

}

package com.dominikgruber.scalatorrent

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.metainfo.{MetaInfo, PieceChecksum}
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

trait TorrentSpec extends ActorSpec {
  outer =>

  def meta: MetaInfo
  val peerFinder = TestProbe("peer-finder")
  val storage = TestProbe("storage")
  val coordinator = TestProbe("coordinator")

  lazy val torrent: ActorRef = {
    def createActor = new Torrent(meta, coordinator.ref, 0, 0) {
      override lazy val peerFinder: ActorRef = outer.peerFinder.ref
      override lazy val storage: ActorRef = outer.storage.ref
      override val checksum: PieceChecksum = Mocks.checksum
    }
    system.actorOf(Props(createActor), "torrent")
  }

}

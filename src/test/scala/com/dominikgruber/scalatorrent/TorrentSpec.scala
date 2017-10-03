package com.dominikgruber.scalatorrent

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import com.dominikgruber.scalatorrent.metainfo.{MetaInfo, PieceChecksum}
import com.dominikgruber.scalatorrent.util.{ActorSpec, Mocks}

trait TorrentSpec extends ActorSpec {
  outer =>

  def meta: MetaInfo
  val tracker = TestProbe("tracker")
  val storage = TestProbe("storage")
  val coordinator = TestProbe("coordinator")

  lazy val torrent: ActorRef = {
    def createActor = new Torrent("", meta, coordinator.ref, 0) {
      override lazy val trackers: Seq[ActorRef] = Seq(outer.tracker.ref)
      override lazy val storage: ActorRef = outer.storage.ref
      override val checksum: PieceChecksum = Mocks.checksum
    }
    system.actorOf(Props(createActor), "torrent")
  }

}

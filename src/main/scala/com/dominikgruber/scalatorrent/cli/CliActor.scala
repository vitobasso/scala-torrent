package com.dominikgruber.scalatorrent.cli

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.dominikgruber.scalatorrent.PeerFinder._
import com.dominikgruber.scalatorrent.cli.CliActor._
import com.dominikgruber.scalatorrent.metainfo.FileMetaInfo
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState.ProgressReport

import scala.concurrent.duration._

case class CliActor() extends Actor with ActorLogging {

  var layout: Layout = Rendering.newLayout(7)
    .updated(6, title)

  var torrent: Option[Torrent] = None //TODO multiple

  override def preStart(): Unit = {
    scheduleRendering()
  }

  override def receive: Receive = {
    case Render => // scheduled
      torrent.foreach(render)
    case meta: FileMetaInfo => //from Coordinator
      torrent = Some(Torrent(meta))
    case report: ProgressReport => //from Torrent
      torrent = torrent.map(_.copy(progress = report))
    case report: PeersReport => //from PeerFinder
      torrent = torrent.map(_.copy(peers = report))
    case CommandResponse(response) => //from UserInteraction
      layout = layout.updated(1, response)
  }

  def render(torrent: Torrent) = {
    updateMeta(torrent.meta)
    updateProgress(torrent.progress)
    updatePeers(torrent.peers)
    Rendering.render(layout)
  }

  def updateMeta(meta: FileMetaInfo): Unit = {
    val size: String = formatBytes(meta.totalBytes)
    val metaLine: String = s"${meta.name}, $size"
    layout = layout.updated(5, metaLine)
  }

  def updateProgress(progress: ProgressReport): Unit = {
    val progressLine: String = s"Progress: ${percent(progress.total)}"
    val bar: String = progressBar(progress.perPiece) + "\n "
    layout = layout.updated(3, progressLine).updated(2, bar)
  }

  def updatePeers(peers: PeersReport): Unit = {
    val countsMsg: String = peers.counts
      .toList.sortBy(_._1)(PeerStatusOrder)
      .map{ case (status, count) => s"$status: $count" }
      .mkString(", ")
    val activity: String = if(peers.isActive) "~" else ""
    val peerLine: String = s"Peers $countsMsg $activity"
    layout = layout.updated(4, peerLine)
  }

  def scheduleRendering(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    context.system.scheduler.schedule(0.millis, updateRate, self, Render)
  }

}

case object CliActor {

  val updateRate: FiniteDuration = 100.millis //TODO config

  case object Render
  case class ReportPlease(listener: ActorRef)
  case class CommandResponse(string: String)

  case class Torrent(meta: FileMetaInfo,
                     progress: ProgressReport = ProgressReport(0, Seq.empty),
                     peers: PeersReport = PeersReport(Map.empty, false))

  private val dots = List('.', ':', '|', '\u2016', '\u2588')
  private val dotStep = 1.toFloat / dots.size

  val PeerStatusOrder: Ordering[PeerStatus] = {
    val ranking: Map[PeerStatus, Int] = Seq(Connected, New, Dead).zipWithIndex.toMap
    Ordering.by(ranking)
  }

  private def progressBar(progressPerPiece: Seq[Double]): String =
    progressPerPiece
      .map(dotChar)
      .toList.mkString

  private def dotChar(part: Double): Char = {
    val index: Int =
      dots.indices.reverse
        .find(_ * dotStep < part)
        .getOrElse(0)
    dots(index)
  }

  def percent(v: Double): String = "%.0f%%" format (v * 100)

  def formatBytes(bytes: Long): String = {
    val (number, unitIndex) = reducedBytes(bytes, 0)
    val unit = byteUnit(unitIndex)
    val formatedNumber = "%.0f".format(number)
    s"$formatedNumber$unit"
  }

  def reducedBytes(value: Double, unit: Int): (Double, Int) =
    if(value < 1024) (value, unit)
    else reducedBytes(value / 1024, unit + 1)

  def byteUnit(unit: Int): String =
    Seq("B", "kB", "MB", "GB", "TB").applyOrElse(unit, (_: Int) => "?")

  val title: String =
    """
      |
      |                    __            __                             __
      |   ______________ _/ /___ _      / /_____  _____________  ____  / /_
      |  / ___/ ___/ __ `/ / __ `/_____/ __/ __ \/ ___/ ___/ _ \/ __ \/ __/
      | (__  ) /__/ /_/ / / /_/ /_____/ /_/ /_/ / /  / /  /  __/ / / / /_
      |/____/\___/\__,_/_/\__,_/      \__/\____/_/  /_/   \___/_/ /_/\__/
      |
    """.stripMargin

}

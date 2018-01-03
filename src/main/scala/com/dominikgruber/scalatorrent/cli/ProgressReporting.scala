package com.dominikgruber.scalatorrent.cli

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState.ProgressReport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object ProgressReporting {

  case object ReportPlease

  def scheduleReport(file: String, torrent: ActorRef)(implicit system: ActorSystem): Unit = {
    val requestActor = createReportRequestActor(file, torrent)
    system.scheduler.schedule(0 millis, 500 millis, requestActor, ReportPlease)
  }

  private def createReportRequestActor(file: String, torrent: ActorRef)(implicit system: ActorSystem): ActorRef = {
    val props = Props(new ReportRequestActor(torrent))
    system.actorOf(props, s"report-request-${file.replace('/', '_')}")
  }

  class ReportRequestActor(torrent: ActorRef) extends Actor with ActorLogging {
    override def receive: Receive = {
      case ReportPlease => //scheduled by Boot
        torrent ! ReportPlease
      case r: ProgressReport => //from Torrent
        showProgress(r)
    }
  }
  def showProgress(progress: ProgressReport): Unit = {
    val msg = progressMessage(progress)
    AnsiEscape.printAbove(1, msg)
  }

  private def progressMessage(progress: ProgressReport): String = {
    import org.jline.terminal.{Terminal, TerminalBuilder}
    val terminal: Terminal = TerminalBuilder.terminal

    val totalSpace = 5
    val barSpace = terminal.getSize.getColumns - totalSpace

    val parts = progress.progressPerPiece
    val dots = parts.size min barSpace
    val partsPerDot = (parts.size.toFloat / dots).ceil.toInt
    val bar = parts.grouped(partsPerDot)
      .map(dotChar)
      .toList.mkString

    val total = percent(progress.overallProgress)
    s"$total $bar"
  }

  private val dots = List('.', ',', ':', ';', '¡', '|', '\u2016', '\u2588')
  private val dotStep = 1.toFloat / dots.size

  private def dotChar(parts: Seq[Double]): Char = {
    val progress = parts.sum/parts.size
    val index: Int =
      dots.indices.reverse
        .find(_ * dotStep < progress)
        .getOrElse(0)
    dots(index)
  }

  def percent(v: Double): String = "%3.0f%%" format (v * 100)

}

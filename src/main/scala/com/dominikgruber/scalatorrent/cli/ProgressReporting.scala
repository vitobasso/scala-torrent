package com.dominikgruber.scalatorrent.cli

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState.ProgressReport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object ProgressReporting {

  val updateRate: FiniteDuration = 100 millis //TODO config

  case object ReportPlease

  def scheduleReport(file: String, torrent: ActorRef)(implicit system: ActorSystem): Unit = {
    val requestActor = createReportRequestActor(file, torrent)
    system.scheduler.schedule(0 millis, updateRate, requestActor, ReportPlease)
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
    val total = percent(progress.overallProgress)
    val bar = progressBar(progress)

    val rendering = new Rendering

    val layout =
      rendering.newLayout
        .addBottom(total)
        .addBottom(bar)
        .addBottom("")

    rendering.render(layout)
  }

  private val dots = List('.', ':', '|', '\u2016', '\u2588')
  private val dotStep = 1.toFloat / dots.size

  private def progressBar(progress: ProgressReport): String =
    progress.progressPerPiece
      .map(dotChar)
      .toList.mkString

  private def dotChar(part: Double): Char = {
    val index: Int =
      dots.indices.reverse
        .find(_ * dotStep < part)
        .getOrElse(0)
    dots(index)
  }

  def percent(v: Double): String = "%3.0f%%" format (v * 100)

}

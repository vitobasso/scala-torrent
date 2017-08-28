package com.dominikgruber.scalatorrent.terminal

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.dominikgruber.scalatorrent.actor.Torrent.ReportPlease
import com.dominikgruber.scalatorrent.transfer.ProgressReport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object ProgressReporting {

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
      case ReportPlease =>
        torrent ! ReportPlease
      case r: ProgressReport =>
        showProgress(r)
    }
  }

  import com.dominikgruber.scalatorrent.terminal.AnsiEscape._
  def showProgress(progress: ProgressReport): Unit = {
    val parts: String = progress.pieceProgress
      .filter{ case (_, p) => p > 0 && p < 1 }
      .mapValues(percent)
      .map{ case (i, p) => s"$i: $p" }
      .mkString(", ")
    val partsSection = if(parts.isEmpty) "" else s", part $parts"
    lineAbove(s"total: ${percent(progress.overallProgress)}$partsSection")
  }

  def percent(v: Double): String = "%.0f%%" format (v * 100)

}

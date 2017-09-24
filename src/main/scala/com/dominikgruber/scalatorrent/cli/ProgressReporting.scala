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
      case ReportPlease =>
        torrent ! ReportPlease
      case r: ProgressReport =>
        showProgress(r)
    }
  }

  import com.dominikgruber.scalatorrent.cli.AnsiEscape._
  def showProgress(progress: ProgressReport): Unit = {
    val parts: String = progress.progressPerPiece
      .zipWithIndex
      .collect{ case (p, i) if p > 0 && p < 1 => s"$i: ${percent(p)}" }
      .mkString(", ")
    val total = percent(progress.overallProgress)
    val partsSection = if(parts.isEmpty) "" else s", part $parts"
    lineAbove(s"total: $total$partsSection")
  }

  def percent(v: Double): String = "%.0f%%" format (v * 100)

}

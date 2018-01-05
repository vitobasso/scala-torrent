package com.dominikgruber.scalatorrent.cli

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState.ProgressReport

import scala.concurrent.duration._

case class FrontendActor() extends Actor with ActorLogging {

  override def receive: Receive = {
    case r: ProgressReport => //from Torrent
      showProgress(r)
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

case object FrontendActor {

  val updateRate: FiniteDuration = 100.millis //TODO config

  case class ReportPlease(listener: ActorRef)

}

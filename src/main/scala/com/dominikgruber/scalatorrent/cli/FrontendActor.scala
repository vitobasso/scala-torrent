package com.dominikgruber.scalatorrent.cli

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.dominikgruber.scalatorrent.peerwireprotocol.TransferState.ProgressReport

import scala.concurrent.duration._
import FrontendActor._

case class FrontendActor() extends Actor with ActorLogging {

  val rendering = new Rendering
  var layout: Layout = rendering.newLayout(5)
    .updated(4, title)

  override def preStart(): Unit = {
    scheduleRendering()
  }

  override def receive: Receive = {
    case Render => // scheduled
      rendering.render(layout)
    case ProgressReport(overall, perPiece) => //from Torrent
      updateProgress(overall, perPiece)
    case CommandResponse(response) =>
      layout = layout.updated(1, response)
  }

  def updateProgress(overall: Double, perPiece: Seq[Double]): Unit = {
    val total = percent(overall)
    val bar = progressBar(perPiece) + "\n "
    layout = layout.updated(3, total).updated(2, bar)
  }

  private val dots = List('.', ':', '|', '\u2016', '\u2588')
  private val dotStep = 1.toFloat / dots.size

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

  def percent(v: Double): String = "%3.0f%%" format (v * 100)

  def scheduleRendering(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global
    context.system.scheduler.schedule(0.millis, updateRate, self, Render)
  }

}

case object FrontendActor {

  val updateRate: FiniteDuration = 100.millis //TODO config

  case object Render
  case class ReportPlease(listener: ActorRef)
  case class CommandResponse(string: String)


  val title: String =
    """                    __            __                             __
      |   ______________ _/ /___ _      / /_____  _____________  ____  / /_
      |  / ___/ ___/ __ `/ / __ `/_____/ __/ __ \/ ___/ ___/ _ \/ __ \/ __/
      | (__  ) /__/ /_/ / / /_/ /_____/ /_/ /_/ / /  / /  /  __/ / / / /_
      |/____/\___/\__,_/_/\__,_/      \__/\____/_/  /_/   \___/_/ /_/\__/
      |
    """.stripMargin

}

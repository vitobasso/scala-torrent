package com.dominikgruber.scalatorrent.util
import akka.actor.{Actor, Cancellable}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Short lived actor dedicated to handle one request.
  * Improves upon the ask pattern by allowing state to be kept in the Actor about the context in which the request started.
  *
  * Inspired by:
  * https://jaxenter.com/tutorial-asynchronous-programming-with-akka-actors-105682.html
  */
trait ExtraPattern {
  selfType: Actor =>

  val timeoutDuration: FiniteDuration
  lazy val timeoutTask: Cancellable = //lazy: wait timeoutDuration init
    context.system.scheduler.scheduleOnce(timeoutDuration) {
      if(context != null) {
        onTimeout()
        context.stop(self)
      }
    }

  def done(): Unit = {
    timeoutTask.cancel()
    context.stop(self)
  }

  def onTimeout(): Unit = ()

}
